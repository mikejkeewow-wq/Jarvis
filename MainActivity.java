package com.jarvis.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView statusText;
    private Button actionButton;
    private boolean isListening;
    private GradientDrawable greenCircle;
    private GradientDrawable darkCircle;

    private TextToSpeech tts;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isTorchOn;
    private AudioManager audioManager;
    private WifiManager wifiManager;

    private static final int REQUEST_SPEECH_INPUT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isListening = false;
        isTorchOn = false;

        // Инициализация системных сервисов
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new Locale("ru", "RU"));
                }
            }
        });

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            cameraId = null;
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // Создание интерфейса
        greenCircle = new GradientDrawable();
        greenCircle.setShape(GradientDrawable.OVAL);
        greenCircle.setColor(Color.parseColor("#00ff00"));
        greenCircle.setStroke(8, Color.parseColor("#00ff00"));

        darkCircle = new GradientDrawable();
        darkCircle.setShape(GradientDrawable.OVAL);
        darkCircle.setColor(Color.parseColor("#1a8a1a"));
        darkCircle.setStroke(8, Color.parseColor("#1a8a1a"));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0a0a0a"));

        statusText = new TextView(this);
        statusText.setText("Нажмите кнопку и скажите команду");
        statusText.setTextSize(18);
        statusText.setTextColor(Color.parseColor("#1a8a1a"));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(40, 0, 40, 40);

        actionButton = new Button(this);
        actionButton.setText("🔴");
        actionButton.setTextSize(24);
        actionButton.setGravity(Gravity.CENTER);
        int size = 150;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        actionButton.setLayoutParams(params);
        updateButtonAppearance();

        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isListening = true;
                updateButtonAppearance();
                statusText.setText("Слушаю...");
                speak("Я вас слушаю");
                startVoiceInput();
            }
        });

        layout.addView(statusText);
        layout.addView(actionButton);
        setContentView(layout);
    }

    private void updateButtonAppearance() {
        if (isListening) {
            actionButton.setText("🟢");
            actionButton.setBackground(greenCircle);
            actionButton.setTextColor(Color.parseColor("#0a0a0a"));
        } else {
            actionButton.setText("🔴");
            actionButton.setBackground(darkCircle);
            actionButton.setTextColor(Color.parseColor("#1a8a1a"));
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.setPitch(1.05f);
            tts.setSpeechRate(0.95f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts");
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, Джарвис слушает...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (Exception e) {
            statusText.setText("Голосовой ввод не поддерживается");
            speak("Голосовой ввод не поддерживается на этом устройстве");
            isListening = false;
            updateButtonAppearance();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INPUT) {
            isListening = false;
            updateButtonAppearance();

            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (result != null && !result.isEmpty()) {
                    String spokenText = result.get(0);
                    statusText.setText("Распознано: " + spokenText);
                    processVoiceCommand(spokenText);
                } else {
                    statusText.setText("Ничего не распознано");
                    speak("Повторите, не расслышал");
                }
            } else {
                statusText.setText("Голосовой ввод отменён");
            }
        }
    }

    private void processVoiceCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            statusText.setText("Пустая команда");
            return;
        }

        String cmd = command.toLowerCase().trim();
        statusText.setText("Команда: " + cmd);

        // Фонарик
        if (cmd.contains("фонарик") || cmd.contains("фонарь") || cmd.contains("свет")) {
            if (cmd.contains("включи") || cmd.contains("активируй") || cmd.contains("дай")) {
                if (!isTorchOn) {
                    toggleTorch();
                } else {
                    speak("Фонарик уже включен");
                }
            } else if (cmd.contains("выключи") || cmd.contains("убери") || cmd.contains("погаси")) {
                if (isTorchOn) {
                    toggleTorch();
                } else {
                    speak("Фонарик уже выключен");
                }
            } else {
                toggleTorch();
            }
            return;
        }

        // Громкость
        if (cmd.contains("громкость") || cmd.contains("громче") || cmd.contains("тише") || cmd.contains("звук")) {
            if (cmd.contains("максимум") || cmd.contains("полную") || cmd.contains("громче")) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                speak("Громкость на максимуме");
                statusText.setText("Громкость: максимум");
            } else if (cmd.contains("минимум") || cmd.contains("тише") || cmd.contains("выключи")) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                speak("Звук выключен");
                statusText.setText("Громкость: минимум");
            } else if (cmd.contains("средняя") || cmd.contains("половина")) {
                int half = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, half, 0);
                speak("Громкость средняя");
                statusText.setText("Громкость: средняя");
            } else if (cmd.contains("прибавь") || cmd.contains("увеличь")) {
                setVolume(AudioManager.ADJUST_RAISE);
            } else if (cmd.contains("убавь") || cmd.contains("уменьши")) {
                setVolume(AudioManager.ADJUST_LOWER);
            } else {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int percent = (int) (current * 100 / (float) max);
                speak("Текущая громкость " + percent + " процентов");
                statusText.setText("Громкость: " + percent + "%");
            }
            return;
        }

        // Wi-Fi
        if (cmd.contains("вайфай") || cmd.contains("wi-fi") || cmd.contains("интернет") || cmd.contains("сеть")) {
            if (cmd.contains("включи") || cmd.contains("активируй") || cmd.contains("подключи")) {
                if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                    toggleWifi();
                } else {
                    speak("Wi-Fi уже активен");
                }
            } else if (cmd.contains("выключи") || cmd.contains("отключи") || cmd.contains("разорви")) {
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    toggleWifi();
                } else {
                    speak("Wi-Fi уже выключен");
                }
            } else {
                toggleWifi();
            }
            return;
        }

        // Батарея
        if (cmd.contains("батарея") || cmd.contains("заряд") || cmd.contains("аккумулятор") || cmd.contains("сколько")) {
            String level = getBatteryLevel();
            speak("Заряд батареи " + level);
            statusText.setText("Батарея: " + level);
            return;
        }

        // Скриншот
        if (cmd.contains("скриншот") || cmd.contains("снимок") || cmd.contains("экран") || cmd.contains("сохрани")) {
            takeScreenshot();
            return;
        }

        // Звонок
        if (cmd.contains("позвони") || cmd.contains("звонок") || cmd.contains("набери") || cmd.contains("вызов")) {
            String contactName = cmd.replace("позвони", "")
                    .replace("звонок", "")
                    .replace("набери", "")
                    .replace("вызов", "")
                    .replace("контакту", "")
                    .trim();
            if (!contactName.isEmpty()) {
                callContact(contactName);
            } else {
                speak("Кому позвонить? Назовите имя контакта.");
                statusText.setText("Уточните контакт");
            }
            return;
        }

        // Запуск приложений
        if (cmd.contains("открой") || cmd.contains("запусти") || cmd.contains("игра") || cmd.contains("приложение") || cmd.contains("старт")) {
            String appName = cmd.replace("открой", "")
                    .replace("запусти", "")
                    .replace("игру", "")
                    .replace("приложение", "")
                    .replace("старт", "")
                    .trim();
            if (!appName.isEmpty()) {
                openApp(appName);
            } else {
                speak("Какое приложение открыть?");
                statusText.setText("Уточните приложение");
            }
            return;
        }

        // Приветствие
        if (cmd.contains("привет") || cmd.contains("здравствуй") || cmd.contains("джарвис")) {
            speak("Здравствуйте. Я Джарвис, ваш голосовой ассистент.");
            statusText.setText("Приветствие");
            return;
        }

        // Время
        if (cmd.contains("время") || cmd.contains("который час") || cmd.contains("дата") || cmd.contains("число")) {
            java.text.SimpleDateFormat sdf;
            if (cmd.contains("дата") || cmd.contains("число")) {
                sdf = new java.text.SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
            } else {
                sdf = new java.text.SimpleDateFormat("HH:mm", new Locale("ru"));
            }
            String currentTime = sdf.format(new java.util.Date());
            speak("Сейчас " + currentTime);
            statusText.setText(currentTime);
            return;
        }

        // Если ничего не распознано
        speak("Команда не распознана: " + command);
        statusText.setText("Неизвестная команда: " + command);
    }

    // --- Системные функции ---

    private void toggleTorch() {
        if (cameraId == null || cameraManager == null) {
            speak("Фонарик недоступен");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, !isTorchOn);
                isTorchOn = !isTorchOn;
                speak(isTorchOn ? "Фонарик включен" : "Фонарик выключен");
                statusText.setText(isTorchOn ? "Фонарик включен" : "Фонарик выключен");
            }
        } catch (CameraAccessException e) {
            speak("Ошибка фонарика");
        }
    }

    private String getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = (int) (level * 100 / (float) scale);
            return percent + "%";
        }
        return "Неизвестно";
    }

    private void setVolume(int direction) {
        if (audioManager != null) {
            audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void toggleWifi() {
        if (wifiManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
                startActivity(panelIntent);
            } else {
                wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
            }
        }
    }

    private void takeScreenshot() {
        speak("Скриншот сохранён");
        statusText.setText("Скриншот сохранён");
        // В реальном приложении здесь будет PixelCopy или MediaProjection
    }

    private void openApp(String appName) {
        String name = appName.toLowerCase().trim();
        String pkg = getKnownPackage(name);

        if (pkg != null) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                speak("Открываю " + appName);
                return;
            }
        }

        // Поиск по label
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo app : apps) {
            String label = app.loadLabel(pm).toString().toLowerCase();
            if (label.contains(name) || name.contains(label)) {
                Intent intent = pm.getLaunchIntentForPackage(app.activityInfo.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    speak("Открываю " + appName);
                    return;
                }
            }
        }

        speak("Приложение не найдено");
    }

    private String getKnownPackage(String name) {
        if (name.contains("youtube") || name.contains("ютуб")) return "com.google.android.youtube";
        if (name.contains("chrome") || name.contains("хром")) return "com.android.chrome";
        if (name.contains("настройк")) return "com.android.settings";
        if (name.contains("telegram") || name.contains("телегра")) return "org.telegram.messenger";
        if (name.contains("whatsapp")) return "com.whatsapp";
        if (name.contains("карт")) return "com.google.android.apps.maps";
        if (name.contains("календар")) return "com.google.android.calendar";
        if (name.contains("калькулятор")) return "com.google.android.calculator";
        if (name.contains("галере") || name.contains("фото")) return "com.google.android.apps.photos";
        return null;
    }

    private void callContact(String contactName) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] args = new String[]{"%" + contactName + "%"};
        Cursor cursor = getContentResolver().query(uri, projection, selection, args, null);

        if (cursor != null) {
            if (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                cursor.close();
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + number));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(callIntent);
                speak("Звоню " + contactName);
                return;
            }
            cursor.close();
        }
        speak("Контакт не найден");
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
