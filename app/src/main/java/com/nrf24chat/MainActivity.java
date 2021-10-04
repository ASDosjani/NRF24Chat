package com.nrf24chat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

public class MainActivity extends AppCompatActivity {

    UsbDevice device;
    UsbManager usbManager;
    UsbDeviceConnection usbConnection;
    UsbSerialDevice serial;
    Button sendbutton, down;
    String ACTION_USB_PERMISSION = "com.heriharan.arduinousb.USB_PERMISSION";
    Handler handler = new Handler();
    LinearLayout msgList;
    ScrollView scroll;
    TextView unreadtv;
    SharedPreferences sp;
    boolean sending = false;
    EditText editText;
    public int currentid, delay, unread=0, notificationid = 1;
    NotificationManagerCompat notificationManager;
    ProgressBar progressBar;

    CountDownTimer repeater = new CountDownTimer(0, 0) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {

        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        startActivity(new Intent(this, SettingsActivity.class));
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        sendbutton = findViewById(R.id.send);
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.scroll);
        editText = findViewById(R.id.edittext);
        progressBar = findViewById(R.id.progressBar);
        down = findViewById(R.id.down);
        unreadtv = findViewById(R.id.unreadtv);

        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        //Notification

        notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "channel1",
                    "Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(usbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(usbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
        onConnect(new View(getApplicationContext()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    if ((msgList.getHeight() - scrollY - 350 * (int) getApplicationContext().getResources().getDisplayMetrics().density) > scroll.getHeight())
                        down.setVisibility(View.VISIBLE);
                    else if ((msgList.getHeight() - scrollY - 60 * (int) getApplicationContext().getResources().getDisplayMetrics().density) < scroll.getHeight()) {
                        down.setVisibility(View.INVISIBLE);
                        unread = 0;
                        unreadtv.setText("");
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sp.getBoolean("repeat", false)) {
            if (!sending) sendbutton.setText("Start sending");
        } else {
            repeater.cancel();
            sending = false;
            progressBar.setProgress(0);
            sendbutton.setText("Send");
        }
        if (!sp.getBoolean("notifications", true)) {
            notificationManager.cancelAll();
            notificationid = 1;
        }
        if (sending && delay != Integer.parseInt(sp.getString("delay", "5")) * 1000) {
            delay = Integer.parseInt(sp.getString("delay", "5")) * 1000;
            progressBar.setMax(delay);
            repeater.cancel();
            repeater = new CountDownTimer(delay, 10) {
                @Override
                public void onTick(long millisUntilFinished) {
                    progressBar.setProgress(delay - (int) millisUntilFinished);
                }

                @Override
                public void onFinish() {
                    if (!editText.getText().toString().isEmpty()) {
                        showMessage(editText.getText().toString(), true);
                        serial.write(((sp.getString("username", "").isEmpty() ? "User" : sp.getString("username", ""))
                                + "&434" + editText.getText().toString() + "\n").getBytes());
                    }
                    if (sending) {
                        repeater.start();
                    }
                }
            };
            repeater.start();
        }
        notificationManager.cancelAll();
        if (down.getVisibility() == View.INVISIBLE)
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scroll.smoothScrollBy(0, 9999999);
                }
            }, 50);
    }

    public void onConnect(View view) {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();

        if (!usbDevices.isEmpty()) {
            if (device == null) {
                boolean keep = true;
                for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                    device = entry.getValue();
                    int deviceVID = device.getVendorId();
                    if (deviceVID == 6790 || deviceVID == 1659 || deviceVID == 1155)// Arduino,STM32 Vendor ID
                    {
                        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(device, pi);
                        keep = false;
                    } else {
                        usbConnection = null;
                        device = null;
                    }
                    if (!keep) break;
                }
            } else {
                if (usbManager.hasPermission(device)) {
                    if (!sp.getBoolean("repeat", false)) {
                        if (!editText.getText().toString().isEmpty()) {
                            showMessage(editText.getText().toString(), true);
                            serial.write(((sp.getString("username", "").isEmpty() ? "User" : sp.getString("username", ""))
                                    + "&434" + editText.getText().toString() + "\n").getBytes());
                            editText.setText("");
                        }
                    } else if (sending) {
                        repeater.cancel();
                        sending = false;
                        progressBar.setProgress(0);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sendbutton.setText("Start sending");
                            }
                        });
                    } else if (sp.getBoolean("repeat", false) && !sending) {
                        sending = true;
                        delay = Integer.parseInt(sp.getString("delay", "5")) * 1000;
                        progressBar.setMax(delay);
                        repeater = new CountDownTimer(delay, 10) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                progressBar.setProgress(delay - (int) millisUntilFinished);
                            }

                            @Override
                            public void onFinish() {
                                if (!editText.getText().toString().isEmpty()) {
                                    showMessage(editText.getText().toString(), true);
                                    serial.write(((sp.getString("username", "").isEmpty() ? "User" : sp.getString("username", ""))
                                            + "&434" + editText.getText().toString() + "\n").getBytes());
                                }
                                if (sending) {
                                    repeater.start();
                                }
                            }
                        };
                        repeater.start();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sendbutton.setText("Stop sending");
                            }
                        });
                    }
                } else {
                    boolean keep = true;
                    for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                        device = entry.getValue();
                        int deviceVID = device.getVendorId();
                        if (deviceVID == 6790 || deviceVID == 1659 || deviceVID == 1155)// Arduino,STM32 Vendor ID
                        {
                            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                            usbManager.requestPermission(device, pi);
                            keep = false;
                        } else {
                            usbConnection = null;
                            device = null;
                        }
                        if (!keep) break;
                    }
                }
            }
        } else device = null;
    }

    public final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    usbConnection = usbManager.openDevice(device);
                    serial = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);
                    if (serial != null) {
                        if (serial.open()) {
                            serial.setBaudRate(115200);
                            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serial.setParity(UsbSerialInterface.PARITY_NONE);
                            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serial.read(mCallback);
                        }
                    }
                } else {
                    Toast.makeText(context, "Please grant the USB Permission", Toast.LENGTH_SHORT).show();
                    onConnect(new View(getApplicationContext()));
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onConnect(new View(getApplicationContext()));
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                if (sp.getBoolean("repeat", false)) {
                    if (sending) repeater.cancel();
                    sending = false;
                    progressBar.setProgress(0);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendbutton.setText("Start sending");
                        }
                    });
                }
            }
        }
    };
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] arg0) {
            String s;
            try {
                s = new String(arg0, "UTF-8");
                if (s.endsWith("\n")) s = s.substring(0, s.length() - 2);
                if (s.equals("success")) handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ((ImageView) findViewById(currentid)).setImageResource(R.drawable.ic_baseline_check_circle_14);
                    }
                }, 100);
                else if (!s.trim().isEmpty() && !s.substring(s.indexOf("&434") + 4).isEmpty())
                    showMessage(s, false);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    public void showMessage(final String m, final boolean right) {
        handler.post(new Runnable() {
                         String message = m;

                         @Override
                         public void run() {
                             int dp = (int) getApplicationContext().getResources().getDisplayMetrics().density;


                             RelativeLayout relativeLayout = new RelativeLayout(getApplicationContext());
                             LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                     LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                             if (right) {
                                 lp.gravity = Gravity.RIGHT;
                                 lp.setMargins(0, 0, 0, 10 * dp);
                                 relativeLayout.setBackgroundResource(R.drawable.background_right);
                                 relativeLayout.setMinimumWidth(100 * dp);

                                 RelativeLayout.LayoutParams messagelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

                                 TextView tv = new TextView(getApplicationContext());
                                 tv.setMaxWidth(350 * dp);
                                 tv.setPadding(15 * dp, 15 * dp, 0, 15 * dp);
                                 tv.setTextColor(Color.WHITE);
                                 tv.setTextSize(20);
                                 tv.setText(message);
                                 tv.setTextIsSelectable(true);
                                 tv.setId(View.generateViewId());


                                 RelativeLayout.LayoutParams timelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                 timelayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                                 timelayout.addRule(RelativeLayout.RIGHT_OF, tv.getId());

                                 TextView time = new TextView(getApplicationContext());
                                 time.setText(getTime());
                                 time.setTextSize(12);
                                 time.setTextColor(Color.LTGRAY);
                                 time.setSingleLine(true);
                                 time.setId(View.generateViewId());

                                 RelativeLayout.LayoutParams successlayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                 successlayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                                 successlayout.addRule(RelativeLayout.RIGHT_OF, time.getId());

                                 ImageView imageView = new ImageView(getApplicationContext());
                                 imageView.setImageResource(R.drawable.ic_baseline_x_14);
                                 imageView.setPadding(5 * dp, 0, 0, 1 * dp);
                                 currentid = View.generateViewId();
                                 imageView.setId(currentid);

                                 RelativeLayout.LayoutParams namelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                 namelayout.addRule(RelativeLayout.ALIGN_RIGHT, imageView.getId());

                                 TextView name = new TextView(getApplicationContext());
                                 name.setText(sp.getString("username", "").isEmpty() ? "User" : sp.getString("username", ""));
                                 name.setTextSize(14);
                                 name.setTextColor(Color.LTGRAY);
                                 name.setSingleLine(true);
                                 name.setPadding(10 * dp, 0, 10 * dp, 0);
                                 name.setId(View.generateViewId());

                                 relativeLayout.addView(imageView, successlayout);
                                 relativeLayout.addView(time, timelayout);
                                 relativeLayout.addView(name, namelayout);
                                 relativeLayout.addView(tv, messagelayout);
                             } else {

                                 String currenttime = getTime();

                                 lp.gravity = Gravity.LEFT;
                                 lp.setMargins(0, 0, 0, 10 * dp);
                                 relativeLayout.setBackgroundResource(R.drawable.background_left);


                                 TextView tv = new TextView(getApplicationContext());
                                 tv.setMaxWidth(350 * dp);
                                 tv.setPadding(0, 15 * dp, 15 * dp, 15 * dp);
                                 tv.setTextColor(Color.WHITE);
                                 tv.setTextSize(20);
                                 tv.setText(message.substring(message.indexOf("&434") + 4));
                                 tv.setTextIsSelectable(true);
                                 tv.setId(View.generateViewId());

                                 RelativeLayout.LayoutParams timelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                 timelayout.setMargins(7 * dp, 0, 0, 0);
                                 timelayout.addRule(RelativeLayout.ALIGN_BOTTOM, tv.getId());

                                 TextView time = new TextView(getApplicationContext());
                                 time.setText(currenttime);
                                 time.setTextSize(12);
                                 time.setTextColor(Color.LTGRAY);
                                 time.setSingleLine(true);
                                 time.setId(View.generateViewId());


                                 RelativeLayout.LayoutParams messagelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                                 messagelayout.addRule(RelativeLayout.RIGHT_OF, time.getId());

                                 RelativeLayout.LayoutParams namelayout = new RelativeLayout.LayoutParams(
                                         RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);


                                 TextView name = new TextView(getApplicationContext());
                                 name.setText(message.substring(0, message.indexOf("&434")));
                                 name.setTextSize(14);
                                 name.setTextColor(Color.LTGRAY);
                                 name.setSingleLine(true);
                                 name.setPadding(10 * dp, 0, 10 * dp, 0);

                                 relativeLayout.addView(name, namelayout);
                                 relativeLayout.addView(tv, messagelayout);
                                 relativeLayout.addView(time, timelayout);

                                 if (down.getVisibility() == View.VISIBLE) {
                                     unread++;
                                     unreadtv.setText(unread+"");
                                 }

                                 if (!foregrounded() && sp.getBoolean("notifications", true)) {
                                     Intent activityIntent = new Intent(getApplicationContext(), MainActivity.class);
                                     activityIntent.setAction(Intent.ACTION_MAIN);
                                     activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                                     PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(),
                                             0, activityIntent, 0);

                                     Notification notification = new NotificationCompat.Builder(getApplicationContext(), "channel1")
                                             .setSmallIcon(R.drawable.ic_baseline_message_24)
                                             .setContentTitle(message.substring(0, message.indexOf("&434")))
                                             .setContentText(message.substring(message.indexOf("&434") + 4))
                                             .setSubText(currenttime)
                                             .setPriority(NotificationCompat.PRIORITY_HIGH)
                                             .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                             .setContentIntent(contentIntent)
                                             .setStyle(new NotificationCompat.BigTextStyle())
                                             .setOnlyAlertOnce(false)
                                             .setAutoCancel(true)
                                             .setGroup("Message Group")
                                             .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                             .build();
                                     Notification groupnotification = new NotificationCompat.Builder(getApplicationContext(), "channel1")
                                             .setSmallIcon(R.drawable.ic_baseline_message_24)
                                             .setGroupSummary(true)
                                             .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                                             .setGroup("Message Group")
                                             .setOnlyAlertOnce(true)
                                             .setAutoCancel(true)
                                             .build();
                                     notificationManager.notify(0, groupnotification);
                                     notificationManager.notify(notificationid++, notification);

                                 }
                             }
                             msgList.addView(relativeLayout, lp);
                         }
                     }
        );
        if (down.getVisibility() == View.INVISIBLE)
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scroll.smoothScrollBy(0, 9999999);
                }
            }, 50);
    }

    public boolean foregrounded() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    public void down(View v) {
        scroll.smoothScrollBy(0, 9999999);
    }

    String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {

        if (getCurrentFocus() != editText && getCurrentFocus() != null)
            getCurrentFocus().clearFocus();
        else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }
}