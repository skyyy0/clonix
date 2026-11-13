package com.example.security

import android.app.admin.DeviceAdminReceiver

/**
 * Admin receiver required by Android before Clonix can ask the user to create a work
 * profile with [android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE].
 * No policy is enforced through it; it only anchors the profile owner relationship.
 */
class ClonixDeviceAdminReceiver : DeviceAdminReceiver()
