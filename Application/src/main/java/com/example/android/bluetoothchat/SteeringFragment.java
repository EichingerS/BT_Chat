package com.example.android.bluetoothchat;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.common.logger.Log;

import java.io.IOException;

import static android.widget.Toast.LENGTH_SHORT;

public class SteeringFragment extends Fragment {

    private static final String TAG = "SteeringFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // INIT of the command array

    int sending_step = 2;
    static byte middle_position = 127;
    int temp_command_seven  = 0;
    int old_progress_driving_seekbar = middle_position;
    int old_progress_steering_seekbar = middle_position;

    byte [] command = { (byte) 0b11111111, // Channel_0
            (byte) middle_position, // Channel_1
            (byte) middle_position, // Channel_2
            (byte) middle_position, // Channel_3
            (byte) middle_position, // Channel_4
            (byte) 0b00000000, // Channel_5
            (byte) 0b00000000, //Channel_6
            (byte) 0b00000000, // Channel_7
            (byte) 0b00000000, // Channel 8
            (byte) 0b01000000 // Channel 9
    };

    // Layout Views
    SeekBar driving_seekbar, steering_seekbar;

    private Button forward_btn, backward_btn, left_btn, right_btn;
    private RadioButton rl_btn, wb_btn, lh_btn, bl_btn, br_btn, al_btn, fl_btn, nl_btn;

    boolean rl_btn_state;
    boolean wb_btn_state;
    boolean lh_btn_state;
    boolean bl_btn_state;
    boolean br_btn_state;
    boolean al_btn_state;
    boolean fl_btn_state;
    boolean nl_btn_state;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    //private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_steering, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {

        steering_seekbar = (SeekBar) view.findViewById(R.id.steer_seekBar);
        driving_seekbar = (SeekBar) view.findViewById(R.id.drive_seekBar);

        driving_seekbar.setMax(255);
        driving_seekbar.setProgress(middle_position);

        steering_seekbar.setMax(255);
        steering_seekbar.setProgress(middle_position);

        rl_btn = view.findViewById(R.id.btn_RL);
        wb_btn = view.findViewById(R.id.btn_WB);
        lh_btn = view.findViewById(R.id.btn_LH);
        bl_btn = view.findViewById(R.id.btn_BL);
        br_btn = view.findViewById(R.id.btn_BR);
        al_btn = view.findViewById(R.id.btn_AL);
        fl_btn = view.findViewById(R.id.btn_FL);
        nl_btn = view.findViewById(R.id.btn_NL);
        forward_btn = (Button) view.findViewById(R.id.btn_forward);
        backward_btn = (Button) view.findViewById(R.id.btn_backward);
        left_btn = (Button) view.findViewById(R.id.btn_left);
        right_btn = (Button) view.findViewById(R.id.btn_right);

        rl_btn_state = rl_btn.isChecked();
        wb_btn_state = wb_btn.isChecked();
        lh_btn_state = lh_btn.isChecked();
        bl_btn_state = bl_btn.isChecked();
        br_btn_state = br_btn.isChecked();
        al_btn_state = al_btn.isChecked();
        fl_btn_state = fl_btn.isChecked();
        nl_btn_state = nl_btn.isChecked();
    }

    /**
     * Set up the UI and background operations for chat.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        //mOutStringBuffer = new StringBuffer("");

        /** Initialize the send buttons with a listener that for click events **/

        //OnTouchListener code for the driving_seekbar from 0 to 255 except 1
        // CHANNEL_ONE
        driving_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (checkPermission() && (progress>0))
                {
                    command[2] = (byte) progress;
                    byte[] send = {0x30, 0x31, command[2]};
                    mChatService.write(send);
                }

                /*
                old_progress_driving_seekbar = (int) command[1];
                if (old_progress_driving_seekbar<0) old_progress_driving_seekbar = (int) old_progress_driving_seekbar+256;

                int temp_cal;
                temp_cal = (old_progress_driving_seekbar-progress);
                if (temp_cal<0) temp_cal = temp_cal*-1;

                if ((temp_cal) > sending_step) {
                    command[1] = (byte) progress;
                    sendMessage("01" + command[1]);
                }*/
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                driving_seekbar.setProgress(middle_position);
                command[2] = (byte) middle_position;
                byte[] send = {0x30, 0x31, command[2]};
                mChatService.write(send);
            }
        });

        //OnTouchListener code for the steering_seekbar from 0 to 255 except 1
        // CHANNEL_TWO
        steering_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(checkPermission() && (progress>0))
                {
                    command[2] = (byte) progress;
                    byte[] send = {0x30, 0x32, command[2]};
                    mChatService.write(send);

                    /*
                    old_progress_steering_seekbar = (int) command[2];
                    if (old_progress_steering_seekbar < 0)
                        old_progress_steering_seekbar = (int) old_progress_steering_seekbar + 256;

                    int temp_cal;
                    temp_cal = (old_progress_steering_seekbar - progress);
                    if (temp_cal < 0) temp_cal = temp_cal * -1;

                    if ((temp_cal) > sending_step) {
                        command[2] = (byte) progress;
                        byte[] send = {0x30, 0x32, command[2]};
                        mChatService.write(send);
                    }
                    */
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                steering_seekbar.setProgress(middle_position);
                command[2] = (byte) middle_position;
                byte[] send = {0x30, 0x32, command[2]};
                mChatService.write(send);
            }
        });

        // CHANNEL_SEVEN
        rl_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        rl_btn_state = !rl_btn_state;
                        rl_btn.setChecked(rl_btn_state);

                        //Toast.makeText(getActivity(), "Button RL clicked", Toast.LENGTH_SHORT).show();
                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b10000000 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;
                        //temp_command_seven = (int) temp_command_seven+256;

                        //String message = "07" + temp_command_seven;
                        //Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else rl_btn.setChecked(false);
                }
            }
        });

        wb_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        wb_btn_state = !wb_btn_state;
                        wb_btn.setChecked(wb_btn_state);

                        temp_command_seven = command[7];
                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00011000 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else wb_btn.setChecked(false);
                }
            }
        });

        lh_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        lh_btn_state = !lh_btn_state;
                        lh_btn.setChecked(lh_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00100000 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else lh_btn.setChecked(false);
                }
            }
        });

        bl_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        bl_btn_state = !bl_btn_state;
                        bl_btn.setChecked(bl_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00010000 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else bl_btn.setChecked(false);
                }
            }
        });

        br_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        br_btn_state = !br_btn_state;
                        br_btn.setChecked(br_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00001000 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else br_btn.setChecked(false);
                }
            }
        });

        al_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        al_btn_state = !al_btn_state;
                        al_btn.setChecked(al_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00000100 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else al_btn.setChecked(false);
                }
            }
        });

        fl_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        fl_btn_state = !fl_btn_state;
                        fl_btn.setChecked(fl_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00000010 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven<0) temp_command_seven = (int) temp_command_seven+256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else fl_btn.setChecked(false);
                }
            }
        });

        nl_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    if(checkPermission()) {
                        nl_btn_state = !nl_btn_state;
                        nl_btn.setChecked(nl_btn_state);

                        temp_command_seven = (byte) ~temp_command_seven;
                        temp_command_seven = (byte) ((byte) 0b00000001 ^ temp_command_seven);
                        temp_command_seven = (byte) ~temp_command_seven;
                        command[7] = (byte) temp_command_seven;

                        if (temp_command_seven < 0)
                            temp_command_seven = (int) temp_command_seven + 256;

                        byte[] send = {0x30, 0x37, command[7]};
                        mChatService.write(send);
                    }
                    else nl_btn.setChecked(false);
                }
            }
        });

        left_btn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(checkPermission())
                        {
                            command[2] = (byte) 1;
                            byte[] send = {0x30, 0x32, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(checkPermission())
                        {
                            command[2] = (byte) middle_position;
                            byte[] send = {0x30, 0x32, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                }
                return false;
            }
        });

        right_btn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(checkPermission())
                        {
                            command[2] = (byte) 255;
                            byte[] send = {0x30, 0x32, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(checkPermission())
                        {
                            command[2] = (byte) middle_position;
                            byte[] send = {0x30, 0x32, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                }
                return false;
            }
        });

        forward_btn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(checkPermission())
                        {
                            command[2] = (byte) 255;
                            byte[] send = {0x30, 0x31, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(checkPermission())
                        {
                            command[2] = (byte) middle_position;
                            byte[] send = {0x30, 0x31, command[1]};
                            mChatService.write(send);
                        }
                        return true;
                }
                return false;
            }
        });

        backward_btn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(checkPermission())
                        {
                            command[2] = (byte) 1;
                            byte[] send = {0x30, 0x31, command[2]};
                            mChatService.write(send);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(checkPermission())
                        {
                            command[2] = (byte) middle_position;
                            byte[] send = {0x30, 0x31, command[1]};
                            mChatService.write(send);
                        }
                        return true;
                }
                return false;
            }
        });

    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private boolean checkPermission()
    {
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return false;
        }
        else return true;
    }

    /**
     * Sends a message.
     *
     * **/

    private void sendBytes(byte one, byte two, int three) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = {one, two, (byte) three};
        mChatService.write(send);
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    break;
                case Constants.MESSAGE_READ:
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    /**NEW**/
                    Log.d(TAG, "BT is enabled");
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }
}
