# Security notes

This application deliberately declares no runtime permissions.

It accesses a user-selected document through Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`) and asks the operating system to persist only that URI grant. It does not enumerate shared storage and does not access the network.

Before installing an APK, verify it was built from this source or built by you. A sideload warning is expected for any APK installed outside an app store and does not by itself prove malware.
