#!/usr/bin/env bash
# دليل معايير القبول على المحاكي:
#  1) رسالة SMS من المرسل alinma تُلتقط والتطبيق مغلق (العملية مقتولة).
#  2) رمز التحقق يُتجاهل، والصيغة المجهولة تذهب إلى "غير مفهومة".
#  3) اختبارات Room: منع التكرار، قائمة الانتظار، إعادة المسح مرتين.
set -euo pipefail

PKG=com.masarif.app
EV=evidence
mkdir -p "$EV"

log() { echo "==> $*"; }

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
# المحاكي بطيء (بلا تسريع رسومي) فقد يتجمّد المشغّل ويُظهر نافذة ANR تغطي التطبيق وتبتلع اللمسات.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
# تجاوز أي نافذة ANR/انهيار ظاهرة الآن (Wait) قبل البدء.
dismiss_system_dialogs() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$EV/ui.xml" >/dev/null 2>&1 || true
  local b
  b=$(python3 - "$EV/ui.xml" <<'PY'
import sys,re,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit(0)
for n in root.iter('node'):
    if (n.get('resource-id') or '') in ('android:id/aerr_wait','android:id/aerr_close') and n.get('bounds'):
        m=re.findall(r'\d+', n.get('bounds')); print((int(m[0])+int(m[2]))//2,(int(m[1])+int(m[3]))//2); break
PY
)
  if [ -n "$b" ]; then echo "dismissing system dialog at $b"; adb shell input tap $b; sleep 2; fi
}
dismiss_system_dialogs

log "install debug apk"
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECEIVE_SMS
adb shell pm grant $PKG android.permission.READ_SMS
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true

log "seed the inbox with one alinma message BEFORE first launch so the onboarding sender check can succeed"
adb emu sms send alinma 'Purchase by mada Pay\nAmount:12 SAR\nMada card:4321*\nAt:SEED SHOP\nOn:26-09-01 09:00'
# انتظر حتى يخزّنها تطبيق الرسائل فعلاً في صندوق الوارد (المحاكي بطيء).
for i in $(seq 1 30); do
  if adb shell content query --uri content://sms/inbox --projection address 2>/dev/null | grep -qi alinma; then echo "inbox has the seeded alinma message"; break; fi
  sleep 3
done
adb shell content query --uri content://sms/inbox --projection address,body 2>/dev/null | head -5 | tee "$EV/inbox_before_launch.txt" || true

log "first launch: onboarding = permissions (granted) -> sender check -> period -> import"
adb logcat -c
dismiss_system_dialogs
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 8
dismiss_system_dialogs
adb shell screencap -p /sdcard/01_first_launch.png && adb pull /sdcard/01_first_launch.png "$EV/" >/dev/null
# الترحيب يتطلب لمسات: تحقق (المرسل الافتراضي alinma) → متابعة → "كل الوقت" → ابدأ الاستيراد → ابدأ.
# نعثر على العناصر عبر uiautomator بمعرّف الاختبار (resource-id = testTag) ونطبع نصوص الشاشة عند الفشل.
dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$EV/ui.xml" >/dev/null 2>&1 || true
}
screen_texts() {
  python3 - "$EV/ui.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception as e: print("  (ui.xml unreadable:", e, ")"); sys.exit(0)
for n in root.iter('node'):
    t=(n.get('text') or '').strip(); rid=(n.get('resource-id') or '').strip()
    if t or rid: print("  text=%r id=%r enabled=%s bounds=%s" % (t[:60], rid, n.get('enabled'), n.get('bounds')))
PY
}
SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $3}')
W=${SIZE%x*}; H=${SIZE#*x}
scroll_down() { adb shell input swipe $(( W / 2 )) $(( H * 7 / 10 )) $(( W / 2 )) $(( H * 3 / 10 )) 300; sleep 1; }
scroll_top()  { for k in 1 2 3; do adb shell input swipe $(( W / 2 )) $(( H * 3 / 10 )) $(( W / 2 )) $(( H * 9 / 10 )) 200; done; sleep 1; }
# tap_id <resource-id>: uiautomator يعرض العناصر المرئية فقط، فنمرّر الشاشة أثناء البحث.
tap_id() {
  local rid="$1"
  scroll_top
  for i in $(seq 1 15); do
    [ "$i" -gt 1 ] && { [ $(( i % 5 )) -eq 0 ] && scroll_top || scroll_down; }
    dismiss_system_dialogs
    dump_ui
    local found
    found=$(python3 - "$EV/ui.xml" "$rid" <<'PY'
import sys,re,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit(0)
for n in root.iter('node'):
    if (n.get('resource-id') or '') == sys.argv[2] and n.get('bounds'):
        if n.get('enabled') == 'false': print("DISABLED"); break
        m=re.findall(r'\d+', n.get('bounds'))
        print((int(m[0])+int(m[2]))//2, (int(m[1])+int(m[3]))//2); break
PY
)
    if [ -n "$found" ] && [ "$found" != "DISABLED" ]; then adb shell input tap $found; echo "tapped $rid at $found"; return 0; fi
    sleep 2
  done
  echo "WARN: element '$rid' not found/enabled. Screen texts:" | tee -a "$EV/process_state.txt"
  screen_texts | tee -a "$EV/process_state.txt"
  return 1
}
# has_id <resource-id>: هل العنصر ظاهر على الشاشة الآن؟
has_id() {
  dump_ui
  python3 - "$EV/ui.xml" "$1" <<'PY'
import sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit(1)
sys.exit(0 if any((n.get('resource-id') or '')==sys.argv[2] for n in root.iter('node')) else 1)
PY
}
# اضغط "تحقق" حتى يظهر صف المرسل المؤكَّد (الفحص يقرأ صندوق الوارد؛ نعيد المحاولة إن تعثّر).
for attempt in 1 2 3 4; do
  tap_id "btn_verify" || true
  for k in $(seq 1 10); do
    has_id "sender_alinma" && break 2
    sleep 2
  done
  echo "sender row not visible yet after attempt $attempt" | tee -a "$EV/process_state.txt"
done
tap_id "btn_continue" && sleep 2
tap_id "period_-1" && sleep 1
tap_id "btn_import" && sleep 10
adb shell screencap -p /sdcard/01b_onboarding_done.png && adb pull /sdcard/01b_onboarding_done.png "$EV/" >/dev/null
tap_id "btn_start" && sleep 3
echo "--- onboarding logcat:"; adb logcat -d | grep -E "Onboarding|MainActivity|InboxScanner|MasarifApp|FATAL|AndroidRuntime: .*Exception" | tail -30 | tee "$EV/logcat_onboarding.txt" || true
dump_ui; echo "--- screen after onboarding:"; screen_texts | head -30

log "send app to background and kill its process (= closed by the user)"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill $PKG || true
sleep 2
if adb shell pidof $PKG >/dev/null 2>&1 && [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; then
  log "process still alive after am kill, retrying"
  sleep 3
  adb shell am kill $PKG || true
  sleep 2
fi
PID_BEFORE="$(adb shell pidof $PKG | tr -d '\r' || true)"
echo "pid before sms: '${PID_BEFORE}'" | tee "$EV/process_state.txt"
if [ -n "$PID_BEFORE" ]; then
  echo "WARNING: app process is still running; the test is weaker but continues" | tee -a "$EV/process_state.txt"
fi

log "send SMS messages from sender 'alinma' while the app is closed"
adb emu sms send alinma 'Purchase by mada Pay\nAmount:47 SAR\nMada card:4321*\nAt:ALDREES Station Company\nOn:26-09-21 15:58'
sleep 4
adb emu sms send alinma 'Online Purchase 19.23 SAR\nmada Card: 4321*\nAccount: *1000\nAt: SKYLINE\nIn: United Kingdom\nOn: 26-09-20 12:05'
sleep 4
adb emu sms send alinma 'Please use the code: 6850\nTo: Alinma App'
sleep 3
adb emu sms send alinma 'Salary credited\nAmount:12000 SAR\nAccount:*1000\nOn:26-09-27 09:00'
sleep 3
# رسالة من مرسل آخر يجب أن تُرفض
adb emu sms send STC 'Purchase by mada Pay\nAmount:99 SAR\nMada card:1111*\nAt:SHOULD NOT BE STORED\nOn:26-09-21 16:00'
sleep 3
# رمز تحقق فيه مبلغ: يجب أن يُتجاهل لا أن يُحسب
adb emu sms send alinma 'Please use the code:6110\nFor card:*4321\nAmount:33.50 SAR\nMerchant:Hungerstation\nOn:26-09-25 22:26'
sleep 3
# راتب: دخل يُصنَّف تلقائياً
adb emu sms send alinma 'Incoming salary transfer\nAmount: 9000 SAR\nAccount: **1000\nOn: 26-08-31 12:38'
sleep 3
# شراء Atheer بصيغة مختصرة
adb emu sms send alinma 'mada Atheer Purchase 6.25 SAR\nCard 4321*\nAt GREENWAY\n25-12-25 01:57'

log "wait until the receiver has handled all 8 messages (the emulator delivers SMS slowly)"
for i in $(seq 1 50); do
  HANDLED=$(adb logcat -d | grep -c "SmsReceiver: sender=" || true)
  [ "${HANDLED:-0}" -ge 8 ] && break
  sleep 3
done
echo "receiver outcomes logged: ${HANDLED:-0} (expect 8)" | tee -a "$EV/process_state.txt"
sleep 3

log "collect receiver log lines"
adb logcat -d | grep -E "SmsReceiver|InboxScanner|Onboarding|FATAL EXCEPTION" | tee "$EV/logcat_receiver.txt" || true

log "dump the database (WAL files included) and inspect it on the host"
for f in masarif.db masarif.db-wal masarif.db-shm; do
  # exec-out ينقل الملف الثنائي كما هو (بلا تحويل نهايات الأسطر)
  adb exec-out run-as $PKG cat databases/$f > "$EV/$f" 2>/dev/null || true
done
ls -la "$EV"
sudo apt-get install -y -qq sqlite3 >/dev/null 2>&1 || true
{
  echo "-- transactions"
  sqlite3 "$EV/masarif.db" "SELECT id, merchantRaw, merchantClean, amountHalalas, channel, categoryId, datetime(dateTime/1000,'unixepoch') FROM transactions;"
  echo "-- unparsed"
  sqlite3 "$EV/masarif.db" "SELECT id, sender, reason, replace(body, char(10), ' | ') FROM unparsed_sms;"
} | tee "$EV/db_dump.txt"

TX_COUNT=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions;")
ALDREES=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw='ALDREES Station Company' AND amountHalalas=4700 AND channel='POS';")
SKYLINE=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw='SKYLINE' AND amountHalalas=1923 AND channel='ONLINE' AND country='United Kingdom';")
OTHER_SENDER=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw LIKE '%SHOULD NOT%';")
UNPARSED=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM unparsed_sms WHERE body LIKE 'Salary credited%';")
OTP=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM unparsed_sms WHERE body LIKE '%6850%' OR body LIKE '%6110%';")
OTP_TX=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw LIKE '%Hungerstation%';")
SALARY=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions t JOIN categories c ON c.id=t.categoryId WHERE t.channel='SALARY' AND t.direction='INCOME' AND c.catKey='salary' AND t.amountHalalas=900000;")
ATHEER=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw='GREENWAY' AND amountHalalas=625 AND channel='POS';")
SEED=$(sqlite3 "$EV/masarif.db" "SELECT COUNT(*) FROM transactions WHERE merchantRaw='SEED SHOP';")

{
  echo "transactions total: $TX_COUNT"
  echo "onboarding import picked up the pre-seeded inbox message: $SEED (expect 1)"
  echo "ALDREES pos 47 SAR stored: $ALDREES (expect 1)"
  echo "SKYLINE online 19.23 SAR + country stored: $SKYLINE (expect 1)"
  echo "message from other sender stored: $OTHER_SENDER (expect 0)"
  echo "unknown-format message in unparsed: $UNPARSED (expect 1)"
  echo "otp in unparsed: $OTP (expect 0)"
  echo "otp-with-amount stored as transaction: $OTP_TX (expect 0)"
  echo "salary auto-categorized as income/راتب: $SALARY (expect 1)"
  echo "atheer short purchase stored: $ATHEER (expect 1)"
} | tee "$EV/assertions.txt"

log "send a few more realistic messages so the screenshots have data (subscriptions, supermarket, restaurant)"
adb emu sms send alinma 'Online Purchase\nAmount:49 SAR\nmada card:4321*\nAccount:*1000\nat:NETFLIX.COM\nOn:26-08-15 10:00'
sleep 3
adb emu sms send alinma 'Online Purchase\nAmount:49 SAR\nmada card:4321*\nAccount:*1000\nat:NETFLIX.COM\nOn:26-09-15 10:00'
sleep 3
adb emu sms send alinma 'Online Purchase\nAmount:115 SAR\nmada card:4321*\nAccount:*1000\nat:Zain Recharge\nOn:26-09-03 09:10'
sleep 3
adb emu sms send alinma 'Purchase by mada Pay\nAmount:236.80 SAR\nMada card:4321*\nAt:PANDA 0412 RIYADH\nOn:26-09-12 19:40'
sleep 3
adb emu sms send alinma 'Purchase by mada Pay\nAmount:64 SAR\nMada card:4321*\nAt:Hungerstation\nOn:26-09-19 21:05'
sleep 3
adb emu sms send alinma 'Purchase by mada Pay\nAmount:18 SAR\nMada card:4321*\nAt:CUPS COFFEE\nOn:26-09-22 08:12'
sleep 12

log "relaunch app and capture one screenshot per tab (also used by README)"
mkdir -p "$EV/screens"
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 6
dismiss_system_dialogs
shot() { adb shell screencap -p "/sdcard/$1.png" && adb pull "/sdcard/$1.png" "$EV/screens/$1.png" >/dev/null; }
for tab in home transactions queue subscriptions settings; do
  tap_id "tab_$tab" || true
  sleep 3
  shot "$tab"
done
cp "$EV/screens/home.png" "$EV/02_after_sms_home.png"; cp "$EV/screens/transactions.png" "$EV/03_transactions.png"
ls -la "$EV/screens"

log "run instrumented tests (in-memory Room + real inbox rescan twice)"
./gradlew --no-daemon :app:connectedDebugAndroidTest 2>&1 | tail -40

log "smoke test the RELEASE apk (R8/minified): install, launch, must stay alive with no crash"
adb logcat -c
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant $PKG android.permission.RECEIVE_SMS
adb shell pm grant $PKG android.permission.READ_SMS
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 6
adb shell screencap -p /sdcard/04_release_launch.png && adb pull /sdcard/04_release_launch.png "$EV/" >/dev/null
RELEASE_PID="$(adb shell pidof $PKG | tr -d '\r' || true)"
adb logcat -d | grep -E "AndroidRuntime|FATAL" | tee "$EV/logcat_release.txt" || true
echo "release pid after launch: '${RELEASE_PID}'" | tee -a "$EV/assertions.txt"
[ -n "$RELEASE_PID" ] || { echo "FAIL: release apk is not running after launch (crash?)"; exit 1; }
if grep -q "FATAL EXCEPTION" "$EV/logcat_release.txt"; then echo "FAIL: release apk crashed"; exit 1; fi

[ "$ALDREES" = "1" ] || { echo "FAIL: ALDREES transaction not captured while app closed"; exit 1; }
[ "$SKYLINE" = "1" ] || { echo "FAIL: SKYLINE transaction not captured"; exit 1; }
[ "$OTHER_SENDER" = "0" ] || { echo "FAIL: message from another sender was stored"; exit 1; }
[ "$UNPARSED" = "1" ] || { echo "FAIL: unknown format not stored in unparsed"; exit 1; }
[ "$OTP" = "0" ] || { echo "FAIL: OTP was not ignored"; exit 1; }
[ "$OTP_TX" = "0" ] || { echo "FAIL: OTP with amount was counted as a transaction"; exit 1; }
[ "$SALARY" = "1" ] || { echo "FAIL: salary not auto-categorized"; exit 1; }
[ "$ATHEER" = "1" ] || { echo "FAIL: atheer short purchase not parsed"; exit 1; }
[ "$SEED" = "1" ] || { echo "FAIL: onboarding import did not pick up the pre-seeded message"; exit 1; }
echo "ALL EMULATOR ASSERTIONS PASSED"
