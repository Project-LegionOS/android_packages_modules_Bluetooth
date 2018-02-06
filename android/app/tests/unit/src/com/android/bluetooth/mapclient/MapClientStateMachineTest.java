/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.mapclient;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.SdpMasRecord;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.bluetooth.R;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MapClientStateMachineTest {
    private static final String TAG = MapClientStateMachineTest.class.getSimpleName();
    private BluetoothAdapter mAdapter;
    private MceStateMachine mMceStateMachine = null;
    private BluetoothDevice mTestDevice;
    private Context mTargetContext;

    private FakeMapClientService mFakeMapClientService;
    private CountDownLatch mLock = null;
    private Handler mHandler;


    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue("Ignore test when MapClientService is not enabled",
                mTargetContext.getResources().getBoolean(R.bool.profile_supported_mapmce));

        // This line must be called to make sure relevant objects are initialized properly
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        mLock = new CountDownLatch(1);
        mFakeMapClientService = new FakeMapClientService(mLock);
        mMceStateMachine = new MceStateMachine(mFakeMapClientService, mTestDevice);
        Assert.assertNotNull(mMceStateMachine);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mHandler = new Handler();
    }

    @After
    public void tearDown() {
        if (!mTargetContext.getResources().getBoolean(R.bool.profile_supported_mapmce)) {
            return;
        }
        if (mMceStateMachine != null) {
            mMceStateMachine.doQuit();
        }
    }

    /**
     * Test that default state is STATE_CONNECTING
     */
    @Test
    public void testDefaultDisconnectedState() {
        Log.i(TAG, "in testDefaultDisconnectedState");
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING, mMceStateMachine.getState());
    }

    /**
     * Test transition from STATE_CONNECTING --> MSG_MAS_CONNECTED
     */
    @Test
    public void testStateTransitionFromConnectingToConnected() {
        Log.i(TAG, "in testStateTransitionFromConnectingToConnected");

        setupSdpRecordReceipt();
        Message msg = Message.obtain(mHandler, MceStateMachine.MSG_MAS_CONNECTED);
        mMceStateMachine.getCurrentState().processMessage(msg);

        // Wait until the message is processed and a broadcast request is sent to
        // to MapClientService to change
        // state from STATE_CONNECTING to STATE_CONNECTED
        try {
            mLock.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED, mMceStateMachine.getState());
    }

    private void setupSdpRecordReceipt() {
        // Setup receipt of SDP record
        SdpMasRecord record = new SdpMasRecord(1, 1, 1, 1, 1, 1, "blah");
        Message msg = Message.obtain(mHandler, MceStateMachine.MSG_MAS_SDP_DONE, record);
        mMceStateMachine.getCurrentState().processMessage(msg);
    }

    private class FakeMapClientService extends MapClientService {
        private final CountDownLatch mLatch;

        FakeMapClientService(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        void cleanupDevice(BluetoothDevice device) {}
        @Override
        public void sendBroadcast(Intent intent, String receiverPermission) {
            int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if (prevState == BluetoothProfile.STATE_CONNECTING
                    && state == BluetoothProfile.STATE_CONNECTED) {
                mLatch.countDown();
            }
        }
    }
}
