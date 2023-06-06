/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class BluetoothManagerServiceTest {
    static int sTimeout = 3000;
    BluetoothManagerService mManagerService;
    Context mContext;
    @Mock BluetoothServerProxy mBluetoothServerProxy;
    @Mock UserManager mUserManager;

    TestLooper mLooper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Mock these functions so security errors won't throw
        doReturn("name")
                .when(mBluetoothServerProxy)
                .settingsSecureGetString(any(), eq(Settings.Secure.BLUETOOTH_NAME));
        doReturn("00:11:22:33:44:55")
                .when(mBluetoothServerProxy)
                .settingsSecureGetString(any(), eq(Settings.Secure.BLUETOOTH_ADDRESS));

        mContext =
                spy(
                        new ContextWrapper(
                                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        doReturn(mUserManager).when(mContext).getSystemService(UserManager.class);

        doReturn(mock(Intent.class))
                .when(mContext)
                .registerReceiverForAllUsers(any(), any(), eq(null), eq(null));

        BluetoothServerProxy.setInstanceForTesting(mBluetoothServerProxy);

        mLooper = new TestLooper();

        mManagerService = new BluetoothManagerService(mContext, mLooper.getLooper());
    }

    @After
    public void tearDown() {
    }

    @Test
    public void onUserRestrictionsChanged_disallowBluetooth_onlySendDisableMessageOnSystemUser()
            throws InterruptedException {
        doReturn(true)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        doReturn(false)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH_SHARING), any());

        // Check if disable message sent once for system user only
        // Since Message object is recycled after processed, use proxy function to get what value

        // test run on user -1, should not turning Bluetooth off
        mManagerService.onUserRestrictionsChanged(UserHandle.CURRENT);
        verify(mBluetoothServerProxy, timeout(sTimeout).times(0))
                .handlerSendWhatMessage(any(), eq(BluetoothManagerService.MESSAGE_DISABLE));

        // called from SYSTEM user, should try to toggle Bluetooth off
        mManagerService.onUserRestrictionsChanged(UserHandle.SYSTEM);
        verify(mBluetoothServerProxy, timeout(sTimeout))
                .handlerSendWhatMessage(any(), eq(BluetoothManagerService.MESSAGE_DISABLE));
    }

    @Test
    public void testApmEnhancementEnabled() {
        mManagerService.setBluetoothModeChangeHelper(new BluetoothModeChangeHelper(mContext));

        // Change the apm enhancement enabled value to 0
        Settings.Global.putInt(mContext.getContentResolver(), "apm_enhancement_enabled", 0);
        assertThat(
                        Settings.Global.getInt(
                                mContext.getContentResolver(), "apm_enhancement_enabled", 0))
                .isEqualTo(0);

        // Confirm that apm enhancement enabled value has been updated to 1
        mManagerService.loadApmEnhancementStateFromResource();
        assertThat(
                        Settings.Global.getInt(
                                mContext.getContentResolver(), "apm_enhancement_enabled", 0))
                .isEqualTo(1);
    }
}
