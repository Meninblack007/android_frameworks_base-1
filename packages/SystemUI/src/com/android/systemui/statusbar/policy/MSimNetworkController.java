/*
 * Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;

import com.android.systemui.R;

public class MSimNetworkController extends NetworkController {
    // debug
    static final String TAG = "StatusBar.MSimNetworkController";
    static final boolean DEBUG = true;
    static final boolean CHATTY = true; // additional diagnostics, but not logspew

    // telephony
    private MSimTelephonyManager mPhone;
    boolean[] mMSimDataConnected;
    IccCardConstants.State[] mMSimState;
    int[] mMSimDataActivity;
    int[] mMSimDataServiceState;
    ServiceState[] mMSimServiceState;
    SignalStrength[] mMSimSignalStrength;
    private PhoneStateListener[] mMSimPhoneStateListener;
    private CharSequence[] mCarrierTextSub;

    String[] mMSimNetworkName;
    String[] mOriginalSpn;
    String[] mOriginalPlmn;
    int[] mMSimPhoneSignalIconId;
    int[] mMSimLastPhoneSignalIconId;
    private int[] mMSimIconId;
    int[] mMSimDataDirectionIconId; // data + data direction on phones
    int[] mMSimDataSignalIconId;
    int[] mMSimDataTypeIconId;
    int[] mNoMSimIconId;
    int[] mMSimMobileActivityIconId; // overlay arrows for data direction

    String[] mMSimContentDescriptionPhoneSignal;
    String[] mMSimContentDescriptionCombinedSignal;
    String[] mMSimContentDescriptionDataType;

    int[] mMSimLastDataDirectionIconId;
    int[] mMSimLastCombinedSignalIconId;
    int[] mMSimLastDataTypeIconId;
    int[] mMSimcombinedSignalIconId;
    int[] mMSimcombinedActivityIconId;
    int[] mMSimLastcombinedActivityIconId;
    int[] mMSimLastSimIconId;
    private int mDefaultSubscription;
    boolean[] mShowSpn;
    boolean[] mShowPlmn;
    String[] mSpn;
    String[] mPlmn;

    ArrayList<MSimSignalCluster> mSimSignalClusters = new ArrayList<MSimSignalCluster>();
    ArrayList<TextView> mSubsLabelViews = new ArrayList<TextView>();

    public interface MSimSignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription);
        void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
                int typeIcon, String contentDescription, String typeContentDescription,
                int noSimIcon, int subscription);
        void setIsAirplaneMode(boolean is, int airplaneIcon);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public MSimNetworkController(Context context) {
        super(context);

        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        mMSimSignalStrength = new SignalStrength[numPhones];
        mMSimDataServiceState = new int[numPhones];
        mMSimServiceState = new ServiceState[numPhones];
        mMSimState = new IccCardConstants.State[numPhones];
        mMSimIconId = new int[numPhones];
        mMSimPhoneSignalIconId = new int[numPhones];
        mMSimDataTypeIconId = new int[numPhones];
        mNoMSimIconId = new int[numPhones];
        mMSimMobileActivityIconId = new int[numPhones];
        mMSimContentDescriptionPhoneSignal = new String[numPhones];
        mMSimLastPhoneSignalIconId = new int[numPhones];
        mMSimNetworkName = new String[numPhones];
        mOriginalSpn = new String[numPhones];
        mOriginalPlmn = new String[numPhones];
        mMSimLastDataTypeIconId = new int[numPhones];
        mMSimDataConnected = new boolean[numPhones];
        mMSimDataSignalIconId = new int[numPhones];
        mMSimDataDirectionIconId = new int[numPhones];
        mMSimLastDataDirectionIconId = new int[numPhones];
        mMSimLastCombinedSignalIconId = new int[numPhones];
        mMSimcombinedSignalIconId = new int[numPhones];
        mMSimcombinedActivityIconId = new int[numPhones];
        mMSimLastcombinedActivityIconId = new int[numPhones];
        mMSimDataActivity = new int[numPhones];
        mMSimContentDescriptionCombinedSignal = new String[numPhones];
        mMSimContentDescriptionDataType = new String[numPhones];
        mMSimLastSimIconId = new int[numPhones];
        mCarrierTextSub = new CharSequence[numPhones];
        mShowSpn = new boolean[numPhones];
        mShowPlmn = new boolean[numPhones];
        mSpn = new String[numPhones];
        mPlmn = new String[numPhones];

        for (int i=0; i < numPhones; i++) {
            mMSimSignalStrength[i] = new SignalStrength();
            mMSimServiceState[i] = new ServiceState();
            mMSimState[i] = IccCardConstants.State.READY;
            // phone_signal
            mMSimPhoneSignalIconId[i] = 0;
            mMSimDataSignalIconId[i] = 0;;
            mMSimLastPhoneSignalIconId[i] = -1;
            mMSimLastDataTypeIconId[i] = -1;
            mMSimDataConnected[i] = false;
            mMSimLastDataDirectionIconId[i] = -1;
            mMSimLastCombinedSignalIconId[i] = -1;
            mMSimcombinedSignalIconId[i] = 0;
            mMSimcombinedActivityIconId[i] = 0;
            mMSimLastcombinedActivityIconId[i] = 0;
            mMSimDataActivity[i] = TelephonyManager.DATA_ACTIVITY_NONE;
            mMSimLastSimIconId[i] = 0;
            mMSimNetworkName[i] = mNetworkNameDefault;
            mMSimDataServiceState[i] = ServiceState.STATE_OUT_OF_SERVICE;
        }

        mDefaultSubscription = MSimTelephonyManager.getDefault().getDefaultSubscription();
        mDataConnected = mMSimDataConnected[mDefaultSubscription];
        mSimState = mMSimState[mDefaultSubscription];
        mDataActivity = mMSimDataActivity[mDefaultSubscription];
        mDataServiceState = mMSimDataServiceState[mDefaultSubscription];
        mServiceState = mMSimServiceState[mDefaultSubscription];
        mSignalStrength = mMSimSignalStrength[mDefaultSubscription];
        mPhoneStateListener = mMSimPhoneStateListener[mDefaultSubscription];

        mNetworkName = mMSimNetworkName[mDefaultSubscription];
        mPhoneSignalIconId = mMSimPhoneSignalIconId[mDefaultSubscription];
        mLastPhoneSignalIconId = mMSimLastPhoneSignalIconId[mDefaultSubscription];
        // data + data direction on phones
        mDataDirectionIconId = mMSimDataDirectionIconId[mDefaultSubscription];
        mDataSignalIconId = mMSimDataSignalIconId[mDefaultSubscription];
        mDataTypeIconId = mMSimDataTypeIconId[mDefaultSubscription];
        mNoSimIconId = mNoMSimIconId[mDefaultSubscription];

        mContentDescriptionPhoneSignal = mMSimContentDescriptionPhoneSignal[mDefaultSubscription];
        mContentDescriptionCombinedSignal = mMSimContentDescriptionCombinedSignal[
                mDefaultSubscription];
        mContentDescriptionDataType = mMSimContentDescriptionDataType[mDefaultSubscription];

        mLastDataDirectionIconId = mMSimLastDataDirectionIconId[mDefaultSubscription];
        mLastCombinedSignalIconId = mMSimLastCombinedSignalIconId[mDefaultSubscription];
        mLastDataTypeIconId = mMSimLastDataTypeIconId[mDefaultSubscription];
        mLastSimIconId = mMSimLastSimIconId[mDefaultSubscription];
    }

    @Override
    protected void createWifiHandler() {
        // wifi
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new MSimWifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }
    }

    @Override
    protected void registerPhoneStateListener(Context context) {
        // telephony
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        mPhone = (MSimTelephonyManager)context.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
        mMSimPhoneStateListener = new PhoneStateListener[numPhones];
        for (int i=0; i < numPhones; i++) {
            mMSimPhoneStateListener[i] = getPhoneStateListener(i);
            mPhone.listen(mMSimPhoneStateListener[i],
                              PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_CALL_STATE
                            | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                            | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        }
    }

    public void addSignalCluster(MSimSignalCluster cluster, int subscription) {
        mSimSignalClusters.add(cluster);
        refreshSignalCluster(cluster, subscription);
    }

    public void refreshSignalCluster(MSimSignalCluster cluster, int subscription) {
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature || mAppOpsStrictEnabled),
                mWifiIconId,
                mWifiActivityIconId,
                mContentDescriptionWifi);
        cluster.setMobileDataIndicators(
                mHasMobileDataFeature,
                mMSimPhoneSignalIconId[subscription],
                mMSimMobileActivityIconId[subscription],
                mMSimDataTypeIconId[subscription],
                mMSimContentDescriptionPhoneSignal[subscription],
                mMSimContentDescriptionDataType[subscription],
                mNoMSimIconId[subscription], subscription);
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mAlwaysShowCdmaRssi ? mPhoneSignalIconId : mWimaxIconId,
                    mMSimMobileActivityIconId[subscription],
                    mMSimDataTypeIconId[subscription],
                    mContentDescriptionWimax,
                    mMSimContentDescriptionDataType[subscription],
                    mNoMSimIconId[subscription], subscription);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators(
                    mHasMobileDataFeature,
                    mShowPhoneRSSIForData ? mMSimPhoneSignalIconId[subscription]
                        : mMSimDataSignalIconId[subscription],
                    mMSimMobileActivityIconId[subscription],
                    mMSimDataTypeIconId[subscription],
                    mMSimContentDescriptionPhoneSignal[subscription],
                    mMSimContentDescriptionDataType[subscription],
                    mNoMSimIconId[subscription], subscription);
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mAirplaneIconId);

        Slog.d(TAG, "refreshSignalCluster, mMSimPhoneSignalIconId[" + subscription + "]="
                    + getResourceName(mMSimPhoneSignalIconId[subscription])
                    + " mMSimDataSignalIconId[" + subscription + "]="
                    + getResourceName(mMSimDataSignalIconId[subscription])
                    + " mMSimDataTypeIconId[" + subscription + "]="
                    + getResourceName(mMSimDataTypeIconId[subscription])
                    + " mMSimMobileActivityIconId[" + subscription + "]="
                    + getResourceName(mMSimMobileActivityIconId[subscription])
                    + " mNoMSimIconId[" + subscription + "]="
                    + getResourceName(mNoMSimIconId[subscription]));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews(mDefaultSubscription);
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            updateSimState(intent);
            for (int sub = 0; sub < MSimTelephonyManager.getDefault().getPhoneCount(); sub++) {
                updateDataIcon(sub);
                refreshViews(sub);
            }
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            final int subscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY, 0);
            Slog.d(TAG, "Received SPN update on sub :" + subscription);
            mShowSpn[subscription] = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
            mSpn[subscription] = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            mShowPlmn[subscription] = intent.getBooleanExtra(
                    TelephonyIntents.EXTRA_SHOW_PLMN, false);
            mPlmn[subscription] = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            mOriginalSpn[subscription] = mSpn[subscription];
            mOriginalPlmn[subscription] = mPlmn[subscription];
            if (mContext.getResources().getBoolean(R.bool.config_monitor_locale_change)) {
                if (mShowSpn[subscription] && mSpn[subscription] != null) {
                    mSpn[subscription] = getLocaleString(mOriginalSpn[subscription]);
                }
                if (mShowPlmn[subscription] && mPlmn[subscription] != null) {
                    mPlmn[subscription] = getLocaleString(mOriginalPlmn[subscription]);
                }
            }
            updateNetworkName(mShowSpn[subscription], mSpn[subscription], mShowPlmn[subscription],
                    mPlmn[subscription], subscription);
            updateCarrierText(subscription);
            refreshViews(subscription);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews(mDefaultSubscription);
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            //parse the string to current language string in public resources
            if (mContext.getResources().getBoolean(R.bool.config_monitor_locale_change)) {
                for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                    if (mShowSpn[i] && mSpn[i] != null) {
                        mSpn[i] = getLocaleString(mOriginalSpn[i]);
                    }
                    if (mShowPlmn[i] && mPlmn[i] != null) {
                        mPlmn[i] = getLocaleString(mOriginalPlmn[i]);
                    }

                    updateNetworkName(mShowSpn[i], mSpn[i], mShowPlmn[i], mPlmn[i], i);
                    updateCarrierText(i);
                    refreshViews(i);
                }
            } else {
                refreshViews(mDefaultSubscription);
            }
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            updateAirplaneMode();
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                updateSimIcon(i);
                updateCarrierText(i);
            }
            refreshViews(mDefaultSubscription);
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews(mDefaultSubscription);
        }
    }

    public void addSubsLabelView(TextView v) {
        mSubsLabelViews.add(v);
    }

    private void updateCarrierText(int sub) {
        int textResId = 0;
        if (mAirplaneMode) {
            textResId = com.android.internal.R.string.lockscreen_airplane_mode_on;
        } else {
            if (DEBUG) {
                Slog.d(TAG, "updateCarrierText for sub:" + sub + " simState =" + mMSimState[sub]);
            }

            switch (mMSimState[sub]) {
                case ABSENT:
                case UNKNOWN:
                case NOT_READY:
                    textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                    break;
                case PIN_REQUIRED:
                    textResId = com.android.internal.R.string.lockscreen_sim_locked_message;
                    break;
                case PUK_REQUIRED:
                    textResId = com.android.internal.R.string.lockscreen_sim_puk_locked_message;
                    break;
                case READY:
                    // If the state is ready, set the text as network name.
                    mCarrierTextSub[sub] = mMSimNetworkName[sub];
                    break;
                case PERM_DISABLED:
                    textResId = com.android.internal.
                            R.string.lockscreen_permanent_disabled_sim_message_short;
                    break;
                case CARD_IO_ERROR:
                    textResId = com.android.internal.R.string.lockscreen_sim_error_message_short;
                    break;
                default:
                    textResId = com.android.internal.R.string.lockscreen_missing_sim_message_short;
                    break;
            }
        }

        if (textResId != 0) {
            mCarrierTextSub[sub] = mContext.getString(textResId);
        }
    }

    private void setCarrierText() {
        String carrierName = mCarrierTextSub[MSimConstants.SUB1]
                  + "    " + mCarrierTextSub[MSimConstants.SUB2];
        if (mContext.getResources().getBoolean(R.bool.config_showDataConnectionView)) {
            for (int i = 0; i < mSubsLabelViews.size(); i++) {
                TextView v = mSubsLabelViews.get(i);
                v.setText(carrierName);
            }
        } else {
            for (int i = 0; i < mMobileLabelViews.size(); i++) {
                TextView v = mMobileLabelViews.get(i);
                v.setText(carrierName);
                v.setVisibility(View.VISIBLE);
            }
        }
    }


    // ===== Telephony ==============================================================

    private PhoneStateListener getPhoneStateListener(int subscription) {
        PhoneStateListener mMSimPhoneStateListener = new PhoneStateListener(subscription) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (DEBUG) {
                    Slog.d(TAG, "onSignalStrengthsChanged received on subscription :"
                        + mSubscription + "signalStrength=" + signalStrength +
                        ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
                }
                mMSimSignalStrength[mSubscription] = signalStrength;
                updateIconSet(mSubscription);
                updateTelephonySignalStrength(mSubscription);
                refreshViews(mSubscription);
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                if (DEBUG) {
                    Slog.d(TAG, "onServiceStateChanged received on subscription :"
                        + mSubscription + "state=" + state.getState());
                }
                mMSimServiceState[mSubscription] = state;
                if (SystemProperties.getBoolean("ro.config.combined_signal", true)) {
                    /*
                     * if combined_signal is set to true only then consider data
                     * service state for signal display
                     */
                    mMSimDataServiceState[mSubscription] =
                        mMSimServiceState[mSubscription].getDataRegState();
                    if (DEBUG) {
                        Slog.d(TAG, "Combining data service state " +
                                mMSimDataServiceState[mSubscription] + " for signal");
                    }
                }
                updateIconSet(mSubscription);
                updateTelephonySignalStrength(mSubscription);
                updateDataNetType(mSubscription);
                updateDataIcon(mSubscription);
                updateNetworkName(mShowSpn[mSubscription], mSpn[mSubscription],
                                mShowPlmn[mSubscription], mPlmn[mSubscription], mSubscription);
                updateCarrierText(mSubscription);

                refreshViews(mSubscription);
            }

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (DEBUG) {
                    Slog.d(TAG, "onCallStateChanged received on subscription :"
                    + mSubscription + "state=" + state);
                }
                // In cdma, if a voice call is made, RSSI should switch to 1x.
                if (isCdma(mSubscription)) {
                    updateTelephonySignalStrength(mSubscription);
                    refreshViews(mSubscription);
                }
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (DEBUG) {
                    Slog.d(TAG, "onDataConnectionStateChanged received on subscription :"
                    + mSubscription + "state=" + state + " type=" + networkType);
                }

                // DSDS case: Data is active only on DDS. Ignore the Data Connection
                // State changed notifications of the other NON-DDS.
                if (mSubscription ==
                        MSimTelephonyManager.getDefault().getPreferredDataSubscription()) {
                    mDataState = state;
                    mDataNetType = networkType;
                }

                updateIconSet(mSubscription);
                updateDataNetType(mSubscription);
                updateDataIcon(mSubscription);
                refreshViews(mSubscription);
            }

            @Override
            public void onDataActivity(int direction) {
                if (DEBUG) {
                    Slog.d(TAG, "onDataActivity received on subscription :"
                        + mSubscription + "direction=" + direction);
                }
                mMSimDataActivity[mSubscription] = direction;
                updateDataIcon(mSubscription);
                refreshViews(mSubscription);
            }
        };
        return mMSimPhoneStateListener;
    }

    // ===== Wifi ===================================================================

    class MSimWifiHandler extends WifiHandler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews(MSimTelephonyManager.getDefault().
                                getPreferredDataSubscription());
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    protected void updateSimState(Intent intent) {
        IccCardConstants.State simState;
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        // Obtain the subscription info from intent.
        int sub = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY, 0);
        Slog.d(TAG, "updateSimState for subscription :" + sub);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            simState = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)
            || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)
            || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
            simState = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason = intent.getStringExtra(IccCardConstants.
                                                            INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                simState = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                simState = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                simState = IccCardConstants.State.PERSO_LOCKED;
            }
        } else {
            simState = IccCardConstants.State.UNKNOWN;
        }
        // Update the sim state and carrier text.
        if (simState != IccCardConstants.State.UNKNOWN && simState != mMSimState[sub]) {
            mMSimState[sub] = simState;
            updateCarrierText(sub);
            Slog.d(TAG, "updateSimState simState =" + mMSimState[sub]);
        }
        updateIconSet(sub);
        updateSimIcon(sub);
        updateDataIcon(sub);
    }

    private boolean isCdma(int subscription) {
        return (mMSimSignalStrength[subscription] != null) &&
                !mMSimSignalStrength[subscription].isGsm();
    }

    private boolean hasService(int subscription) {
        ServiceState ss = mMSimServiceState[subscription];
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private final void updateTelephonySignalStrength(int subscription) {
        Slog.d(TAG, "updateTelephonySignalStrength: subscription =" + subscription);
        int dataSub = MSimTelephonyManager.getDefault().getPreferredDataSubscription();

        if (!hasService(subscription) &&
                (mMSimDataServiceState[subscription] != ServiceState.STATE_IN_SERVICE)) {
            if (DEBUG) Slog.d(TAG, " No service");
            mMSimPhoneSignalIconId[subscription] =
                    TelephonyIcons.getSignalNullIcon(subscription);
            mMSimDataSignalIconId[subscription] =
                    mMSimPhoneSignalIconId[subscription];
            if (dataSub == subscription)  mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
        } else {
            if (mMSimSignalStrength[subscription] == null || (mMSimServiceState == null)) {
                if (DEBUG) {
                    Slog.d(TAG, " Null object, mMSimSignalStrength= "
                            + mMSimSignalStrength[subscription]
                            + " mMSimServiceState " + mMSimServiceState[subscription]);
                }
                mMSimPhoneSignalIconId[subscription] =
                        TelephonyIcons.getSignalNullIcon(subscription);
                mMSimDataSignalIconId[subscription] = mMSimPhoneSignalIconId[subscription];
                mMSimContentDescriptionPhoneSignal[subscription] =
                        TelephonyIcons.getSignalStrengthDes(subscription, 0);
                if (dataSub == subscription) {
                    mQSPhoneSignalIconId = R.drawable.ic_qs_signal_no_signal;
                }
            } else {
                int iconLevel;
                if (isCdma(subscription) && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel = iconLevel = mMSimSignalStrength[subscription].getCdmaLevel();
                    if(DEBUG) Slog.d(TAG, "mAlwaysShowCdmaRssi= " + mAlwaysShowCdmaRssi
                            + " set to cdmaLevel= "
                            + mMSimSignalStrength[subscription].getCdmaLevel()
                            + " instead of level= " + mMSimSignalStrength[subscription].getLevel());
                } else {
                    mLastSignalLevel = iconLevel = mMSimSignalStrength[subscription].getLevel();
                    if (mShowRsrpSignalLevelforLTE) {
                        if (mMSimServiceState[subscription].getDataNetworkType() ==
                                TelephonyManager.NETWORK_TYPE_LTE) {
                            int level = mMSimSignalStrength[subscription].getAlternateLteLevel();
                            mLastSignalLevel = iconLevel = (level == -1 ? 0 : level);
                            Slog.d(TAG, "updateTelephonySignalStrength, data type is lte, level = "
                                + level + " | " + mMSimSignalStrength[subscription]);
                        }
                    }
                }

                mMSimPhoneSignalIconId[subscription] =
                        TelephonyIcons.getSignalStrengthIcon(subscription, mInetCondition,
                        iconLevel, isRoaming(subscription));

                mMSimContentDescriptionPhoneSignal[subscription] =
                        TelephonyIcons.getSignalStrengthDes(subscription, iconLevel);

                mMSimDataSignalIconId[subscription] = mMSimPhoneSignalIconId[subscription];

                if (dataSub == subscription) {
                    mQSPhoneSignalIconId =
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH[mInetCondition][iconLevel];
                }

                Slog.d(TAG, "updateTelephonySignalStrength, sub: " + subscription
                    + " level=" + iconLevel
                    + " mInetCondition=" + mInetCondition
                    + " mMSimPhoneSignalIconId[" + subscription + "]="
                    + mMSimPhoneSignalIconId[subscription]
                    + "/" + getResourceName(mMSimPhoneSignalIconId[subscription]));
            }
        }
    }

    private boolean isRoaming(int subscription) {
        return (isCdma(subscription) && isCdmaEri(subscription))
                || mPhone.isNetworkRoaming(subscription);
    }

    private final void updateDataNetType(int subscription) {
        // DSDS case: Data is active only on DDS. Clear the icon for NON-DDS
        int dataSub = MSimTelephonyManager.getDefault().getPreferredDataSubscription();
        if (subscription != dataSub) {
            Slog.d(TAG,"updateDataNetType: SUB" + subscription
                    + " is not DDS(=SUB" + dataSub + ")!");
            mMSimDataTypeIconId[subscription] = 0;
        } else {
            mNetworkName = mMSimNetworkName[subscription];
            if (mIsWimaxEnabled && mWimaxConnected) {
                // wimax is a special 4g network not handled by telephony
                mMSimDataTypeIconId[subscription] = R.drawable.stat_sys_data_fully_connected_4g;
                mMSimContentDescriptionDataType[subscription] = mContext.getString(
                        R.string.accessibility_data_connection_4g);
                mQSDataTypeIconId = TelephonyIcons.QS_DATA_4G[mInetCondition];
            } else {
                Slog.d(TAG,"updateDataNetType sub = " + subscription
                        + " mDataNetType = " + mDataNetType);
                mMSimDataTypeIconId[subscription] =
                        TelephonyIcons.getDataTypeIcon(subscription);
                mMSimContentDescriptionDataType[subscription] =
                        TelephonyIcons.getDataTypeDesc();
                mQSDataTypeIconId =
                        TelephonyIcons.getQSDataTypeIcon(subscription);
                Slog.d(TAG, "updateDataNetType, mQSDataTypeIconId = "
                        + getResourceName(mQSDataTypeIconId));
            }
        }

        boolean setQSDataTypeIcon = false;
        if (isCdma(subscription)) {
            if (isCdmaEri(subscription)) {
                mMSimDataTypeIconId[subscription] = R.drawable.stat_sys_data_fully_connected_roam;
                setQSDataTypeIcon = true;
            }
        } else if (mPhone.isNetworkRoaming(subscription)) {
            mMSimDataTypeIconId[subscription] = R.drawable.stat_sys_data_fully_connected_roam;
            setQSDataTypeIcon = true;
        }

        if (setQSDataTypeIcon && subscription == dataSub) {
            mQSDataTypeIconId = TelephonyIcons.QS_DATA_R[mInetCondition];
        }
    }

    boolean isCdmaEri(int subscription) {
        if ((mMSimServiceState[subscription] != null)
                && (hasService(subscription) || (mMSimDataServiceState[subscription]
                == ServiceState.STATE_IN_SERVICE))) {
            final int iconIndex = mMSimServiceState[subscription].getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mMSimServiceState[subscription].getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateIconSet(int subscription) {
        Slog.d(TAG, "updateIconSet, subscription = " + subscription);
        int voiceNetworkType = mMSimServiceState[subscription].getVoiceNetworkType();
        int dataNetworkType =  mMSimServiceState[subscription].getDataNetworkType();
        Slog.d(TAG, "updateIconSet, voice network type is: " + voiceNetworkType
            + "/" + TelephonyManager.getNetworkTypeName(voiceNetworkType)
            + ", data network type is: " + dataNetworkType
            + "/" + TelephonyManager.getNetworkTypeName(dataNetworkType));

        int chosenNetworkType = ((dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetworkType : dataNetworkType);

        Slog.d(TAG, "updateIconSet, chosenNetworkType=" + chosenNetworkType
            + " hspaDataDistinguishable=" + String.valueOf(mHspaDataDistinguishable)
            + " showAtLeastThreeGees=" + String.valueOf(mShowAtLeastThreeGees));

        TelephonyIcons.updateDataType(subscription, chosenNetworkType, mShowAtLeastThreeGees,
            mShow4GforLTE, mHspaDataDistinguishable, mInetCondition);
    }

    private final void updateSimIcon(int cardIndex) {
        Slog.d(TAG,"In updateSimIcon card =" + cardIndex + ", simState= " + mMSimState[cardIndex]);
        if (mMSimState[cardIndex] ==  IccCardConstants.State.ABSENT) {
            mNoMSimIconId[cardIndex] = TelephonyIcons.getNoSimIcon(cardIndex);
        } else {
            mNoMSimIconId[cardIndex] = 0;
        }
        refreshViews(cardIndex);
    }

    private final void updateDataIcon(int subscription) {
        Slog.d(TAG,"updateDataIcon subscription =" + subscription);
        int iconId = 0;
        boolean visible = true;

        int dataSub = MSimTelephonyManager.getDefault().getPreferredDataSubscription();
        Slog.d(TAG,"updateDataIcon dataSub =" + dataSub);
        // DSDS case: Data is active only on DDS. Clear the icon for NON-DDS
        if (subscription != dataSub) {
            mMSimDataConnected[subscription] = false;
            Slog.d(TAG,"updateDataIconi: SUB" + subscription
                     + " is not DDS.  Clear the mMSimDataConnected Flag and return");
            return;
        }

        Slog.d(TAG,"updateDataIcon  when SimState =" + mMSimState[subscription]);
        if (mDataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            // If data network type is unknown do not display data icon
            visible = false;
        } else if (!isCdma(subscription)) {
             Slog.d(TAG,"updateDataIcon  when gsm mMSimState =" + mMSimState[subscription]);
            // GSM case, we have to check also the sim state
            if (mMSimState[subscription] == IccCardConstants.State.READY ||
                mMSimState[subscription] == IccCardConstants.State.UNKNOWN) {
                if (mDataState == TelephonyManager.DATA_CONNECTED) {
                    iconId = TelephonyIcons.getDataActivity(subscription,
                            mMSimDataActivity[subscription]);
                    mMSimDataDirectionIconId[subscription] = iconId;
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                Slog.d(TAG,"updateDataIcon when no sim");
                iconId = TelephonyIcons.getNoSimIcon(subscription);
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mMSimDataActivity can be also DATA_ACTIVITY_DORMANT
            if (mDataState == TelephonyManager.DATA_CONNECTED) {
                iconId = TelephonyIcons.getDataActivity(subscription,
                        mMSimDataActivity[subscription]);
            } else {
                iconId = 0;
                visible = false;
            }
        }

        mMSimDataDirectionIconId[subscription] = iconId;
        mMSimMobileActivityIconId[subscription] = iconId;
        mMSimDataConnected[subscription] = visible;

        Slog.d(TAG,"updateDataIcon when mMSimDataConnected[" + subscription + "] ="
            + mMSimDataConnected[subscription]
            + " mMSimMobileActivityIconId[" + subscription +"] = "
            + mMSimMobileActivityIconId[subscription]);
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn,
            int subscription) {
        if (DEBUG) {
            Slog.d(TAG, "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            if(mContext.getResources().getBoolean(R.bool.config_display_rat) &&
                    mMSimServiceState[subscription] != null) {
                plmn = appendRatToNetworkName(plmn, mMSimServiceState[subscription]);
            }
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mNetworkNameSeparator);
            }
            if(mContext.getResources().getBoolean(R.bool.config_display_rat) &&
                    mMSimServiceState[subscription] != null) {
                spn = appendRatToNetworkName(spn, mMSimServiceState[subscription]);
            }
            str.append(spn);
            something = true;
        }
        if (something) {
            mMSimNetworkName[subscription] = str.toString();
        } else {
            mMSimNetworkName[subscription] = mNetworkNameDefault;
        }

        Slog.d(TAG, "mMSimNetworkName[subscription] " + mMSimNetworkName[subscription]
                                                      + "subscription " + subscription);
    }

    // ===== Full or limited Internet connectivity ==================================
    @Override
    protected void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Slog.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Slog.d(TAG, "updateConnectivity: networkInfo=" + info);
            Slog.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);
        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateWimaxIcons();
        for (int sub = 0; sub < MSimTelephonyManager.getDefault().getPhoneCount(); sub++) {
            updateDataNetType(sub);
            updateDataIcon(sub);
            updateTelephonySignalStrength(sub);
        }
        updateWifiIcons();
    }

    // ===== Update the views =======================================================

    protected void refreshViews(int subscription) {
        Context context = mContext;

        String combinedLabel = "";
        String mobileLabel = "";
        String wifiLabel = "";
        int N;
        Slog.d(TAG,"refreshViews subscription =" + subscription + "mMSimDataConnected ="
                + mMSimDataConnected[subscription]);
        Slog.d(TAG,"refreshViews mMSimDataActivity =" + mMSimDataActivity[subscription]);
        if (!mHasMobileDataFeature) {
            mMSimDataSignalIconId[subscription] = mMSimPhoneSignalIconId[subscription] = 0;
            mobileLabel = "";
            mQSPhoneSignalIconId = 0;
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mMSimDataConnected[subscription]) {
                mobileLabel = mMSimNetworkName[subscription];
            } else if (mConnected) {
                if (hasService(subscription)) {
                    mobileLabel = mMSimNetworkName[subscription];
                } else {
                    mobileLabel = "";
                }
            } else {
                mobileLabel
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mMSimDataConnected[subscription]) {
                mMSimcombinedSignalIconId[subscription] = mMSimDataSignalIconId[subscription];

                combinedLabel = mobileLabel;
                mMSimcombinedActivityIconId[subscription] = mMSimMobileActivityIconId[subscription];
                // set by updateDataIcon()
                mMSimcombinedSignalIconId[subscription] = mMSimDataSignalIconId[subscription];
                mMSimContentDescriptionCombinedSignal[subscription] =
                        mMSimContentDescriptionDataType[subscription];
            } else {
                mMSimMobileActivityIconId[subscription] = 0;
                mMSimcombinedActivityIconId[subscription] = 0;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(
                        R.string.status_bar_settings_signal_meter_wifi_nossid);
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }

                switch (mWifiActivity) {
                    case WifiManager.DATA_ACTIVITY_IN:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_in;
                        break;
                    case WifiManager.DATA_ACTIVITY_OUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_out;
                        break;
                    case WifiManager.DATA_ACTIVITY_INOUT:
                        mWifiActivityIconId = R.drawable.stat_sys_wifi_inout;
                        break;
                    case WifiManager.DATA_ACTIVITY_NONE:
                        mWifiActivityIconId = 0;
                        break;
                }
            }

            mMSimcombinedActivityIconId[subscription] = mWifiActivityIconId;
            combinedLabel = wifiLabel;
            mMSimcombinedSignalIconId[subscription] = mWifiIconId; // set by updateWifiIcons()
            mMSimContentDescriptionCombinedSignal[subscription] = mContentDescriptionWifi;
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(
                        R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            mMSimcombinedSignalIconId[subscription] = mBluetoothTetherIconId;
            mMSimContentDescriptionCombinedSignal[subscription] = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType ==
                ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            // TODO: icons and strings for Ethernet connectivity
            combinedLabel = mConnectedNetworkTypeName;
        }

        if (mAirplaneMode &&
                (mMSimServiceState[subscription] == null || (!hasService(subscription)
                    && !mMSimServiceState[subscription].isEmergencyOnly()))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mMSimContentDescriptionPhoneSignal[subscription] = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = R.drawable.stat_sys_signal_flightmode;
            mMSimPhoneSignalIconId[subscription] = mMSimDataSignalIconId[subscription]
                    = mMSimDataTypeIconId[subscription] = 0;
            mNoMSimIconId[subscription] = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(
                            R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mMSimContentDescriptionCombinedSignal[subscription] =
                        mContentDescriptionPhoneSignal;
                mMSimcombinedSignalIconId[subscription] = mMSimDataSignalIconId[subscription];
            }
            mMSimDataTypeIconId[subscription] = 0;
            mNoMSimIconId[subscription] = 0;

            mMSimcombinedSignalIconId[subscription] = mMSimDataSignalIconId[subscription];
        }
        else if (!mMSimDataConnected[subscription] && !mWifiConnected && !mBluetoothTethered &&
                !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(
                    R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            mMSimcombinedSignalIconId[subscription] =
                    mHasMobileDataFeature ? mMSimDataSignalIconId[subscription] : mWifiIconId;
            mMSimContentDescriptionCombinedSignal[subscription] = mHasMobileDataFeature
                    ? mMSimContentDescriptionDataType[subscription] : mContentDescriptionWifi;
        }

        if (!mMSimDataConnected[subscription]) {
            Slog.d(TAG, "refreshViews: Data not connected!! Set no data type icon / Roaming for"
                    + " subscription: " + subscription);
            mMSimDataTypeIconId[subscription] = 0;
            if (isCdma(subscription)) {
                if (isCdmaEri(subscription)) {
                    mMSimDataTypeIconId[subscription] =
                            R.drawable.stat_sys_data_fully_connected_roam;
                }
            } else if (mPhone.isNetworkRoaming(subscription)) {
                mMSimDataTypeIconId[subscription] = R.drawable.stat_sys_data_fully_connected_roam;
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "refreshViews connected={"
                    + (mWifiConnected?" wifi":"")
                    + (mMSimDataConnected[subscription]?" data":"")
                    + " } level="
                    + ((mMSimSignalStrength[subscription] == null)?"??":Integer.toString
                            (mMSimSignalStrength[subscription].getLevel()))
                    + " mMSimcombinedSignalIconId=0x"
                    + Integer.toHexString(mMSimcombinedSignalIconId[subscription])
                    + "/" + getResourceName(mMSimcombinedSignalIconId[subscription])
                    + " mMSimcombinedActivityIconId=0x" + Integer.toHexString
                            (mMSimcombinedActivityIconId[subscription])
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mMSimDataActivity=" + mMSimDataActivity[subscription]
                    + " mMSimPhoneSignalIconId=0x" + Integer.toHexString
                            (mMSimPhoneSignalIconId[subscription])
                    + "/" + getResourceName(mMSimPhoneSignalIconId[subscription])
                    + " mMSimDataDirectionIconId=0x" + Integer.toHexString
                            (mMSimDataDirectionIconId[subscription])
                    + " mMSimDataSignalIconId=0x" + Integer.toHexString
                            (mMSimDataSignalIconId[subscription])
                    + " mMSimDataTypeIconId=0x" + Integer.toHexString
                            (mMSimDataTypeIconId[subscription])
                    + "/" + getResourceName(mMSimDataTypeIconId[subscription])
                    + " mNoMSimIconId=0x" + Integer.toHexString(mNoMSimIconId[subscription])
                    + "/" + getResourceName(mNoMSimIconId[subscription])
                    + " mMSimMobileActivityIconId=0x"
                    + Integer.toHexString(mMSimMobileActivityIconId[subscription])
                    + "/" + getResourceName(mMSimMobileActivityIconId[subscription])
                    + " mWifiIconId=0x" + Integer.toHexString(mWifiIconId)
                    + " mBluetoothTetherIconId=0x" + Integer.toHexString(mBluetoothTetherIconId));
        }

        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        if (mMSimLastPhoneSignalIconId[subscription] != mMSimPhoneSignalIconId[subscription]
         || mLastWifiIconId                 != mWifiIconId
         || mLastWimaxIconId                != mWimaxIconId
         || mMSimLastDataTypeIconId[subscription] != mMSimDataTypeIconId[subscription]
         || mLastAirplaneMode               != mAirplaneMode
         || mMSimLastSimIconId[subscription] != mNoMSimIconId[subscription]
         || mMSimLastcombinedActivityIconId[subscription]
                != mMSimcombinedActivityIconId[subscription])
        {
            // NB: the mLast*s will be updated later
            for (MSimSignalCluster cluster : mSimSignalClusters) {
                refreshSignalCluster(cluster, subscription);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        // the phone icon on phones
        if (mMSimLastPhoneSignalIconId[subscription] != mMSimPhoneSignalIconId[subscription]) {
            mMSimLastPhoneSignalIconId[subscription] = mMSimPhoneSignalIconId[subscription];
        }

        // the data icon on phones
        if (mMSimLastDataDirectionIconId[subscription] != mMSimDataDirectionIconId[subscription]) {
            mMSimLastDataDirectionIconId[subscription] = mMSimDataDirectionIconId[subscription];
        }

        if (mMSimLastSimIconId[subscription] != mNoMSimIconId[subscription]) {
            mMSimLastSimIconId[subscription] = mNoMSimIconId[subscription];
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
        }
        // the combined data signal icon
        if (mMSimLastCombinedSignalIconId[subscription] !=
                mMSimcombinedSignalIconId[subscription]) {
            mMSimLastCombinedSignalIconId[subscription] = mMSimcombinedSignalIconId[subscription];
        }
        // the combined data activity icon
        if (mMSimLastcombinedActivityIconId[subscription] !=
                mMSimcombinedActivityIconId[subscription]) {
            mMSimLastcombinedActivityIconId[subscription]
                    = mMSimcombinedActivityIconId[subscription];
        }
        // the data network type overlay
        if (mMSimLastDataTypeIconId[subscription] != mMSimDataTypeIconId[subscription]) {
            mMSimLastDataTypeIconId[subscription] = mMSimDataTypeIconId[subscription];
        }

      // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        //Update mobile label only for preferred data subscription and in airplane mode
        if (subscription ==
                MSimTelephonyManager.getDefault().getPreferredDataSubscription() || mAirplaneMode) {
            // mobile label
            N = mMobileLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mMobileLabelViews.get(i);
                v.setText(mobileLabel);
                if ("".equals(mobileLabel)) {
                    v.setVisibility(View.GONE);
                } else {
                    v.setVisibility(View.VISIBLE);
                }
            }
        }
        setCarrierText();
    }

    public int getVoiceNetworkType(int sub) {
        if (mMSimServiceState[sub] == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mMSimServiceState[sub].getVoiceNetworkType();
    }

    public int getDataNetworkType(int sub) {
        if (mMSimServiceState[sub] == null) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        return mMSimServiceState[sub].getDataNetworkType();
    }

    public int getGsmSignalLevel(int sub) {
        if (mMSimSignalStrength[sub] == null) {
            return mMSimSignalStrength[sub].SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
        return mMSimSignalStrength[sub].getGsmLevel();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args, int subscription) {
        pw.println("NetworkController for SUB : " + subscription + " state:");
        pw.println(String.format("  %s network type %d (%s)",
                mConnected?"CONNECTED":"DISCONNECTED",
                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        pw.print("  hasService()=");
        pw.println(hasService(subscription));
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mMSimDataConnected=");
        pw.println(mMSimDataConnected[subscription]);
        pw.print("  mMSimState=");
        pw.println(mMSimState[subscription]);
        pw.print("  mPhoneState=");
        pw.println(mPhoneState);
        pw.print("  mDataState=");
        pw.println(mDataState);
        pw.print("  mMSimDataActivity=");
        pw.println(mMSimDataActivity[subscription]);
        pw.print("  mDataNetType=");
        pw.print(mDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mDataNetType));
        pw.print("  mMSimServiceState=");
        pw.println(mMSimServiceState[subscription]);
        pw.print("  mMSimSignalStrength=");
        pw.println(mMSimSignalStrength[subscription]);
        pw.print("  mLastSignalLevel");
        pw.println(mLastSignalLevel);
        pw.print("  mMSimNetworkName=");
        pw.println(mMSimNetworkName[subscription]);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mMSimPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimPhoneSignalIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimPhoneSignalIconId[subscription]));
        pw.print("  mMSimDataDirectionIconId=");
        pw.print(Integer.toHexString(mMSimDataDirectionIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataDirectionIconId[subscription]));
        pw.print("  mMSimDataSignalIconId=");
        pw.print(Integer.toHexString(mMSimDataSignalIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataSignalIconId[subscription]));
        pw.print("  mMSimDataTypeIconId=");
        pw.print(Integer.toHexString(mMSimDataTypeIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimDataTypeIconId[subscription]));

        pw.println("  - wifi ------");
        pw.print("  mWifiEnabled=");
        pw.println(mWifiEnabled);
        pw.print("  mWifiConnected=");
        pw.println(mWifiConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiIconId=0x%08x/%s",
                    mWifiIconId, getResourceName(mWifiIconId)));
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled="); pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected="); pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle="); pw.println(mWimaxIdle);
            pw.println(String.format("  mWimaxIconId=0x%08x/%s",
                        mWimaxIconId, getResourceName(mWimaxIconId)));
            pw.println(String.format("  mWimaxSignal=%d", mWimaxSignal));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);

        pw.println("  - icons ------");
        pw.print("  mMSimLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimLastPhoneSignalIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastPhoneSignalIconId[subscription]));
        pw.print("  mMSimLastDataDirectionIconId=0x");
        pw.print(Integer.toHexString(mMSimLastDataDirectionIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastDataDirectionIconId[subscription]));
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mMSimLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mMSimLastCombinedSignalIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastCombinedSignalIconId[subscription]));
        pw.print("  mMSimLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mMSimLastDataTypeIconId[subscription]));
        pw.print("/");
        pw.println(getResourceName(mMSimLastDataTypeIconId[subscription]));
        pw.print("  mMSimLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
    }
}
