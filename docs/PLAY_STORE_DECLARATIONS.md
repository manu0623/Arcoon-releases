# Google Play Store Declarations

## QUERY_ALL_PACKAGES Permission

### Declaration Type
Core App Functionality - Device Launcher

### Justification
Argosy is a home screen launcher application. The QUERY_ALL_PACKAGES permission is required for the following core functionality:

1. **App Discovery**: As a launcher, Argosy must query and display all user-installed applications to provide the primary interface for launching apps on the device.

2. **App Grid Display**: The launcher displays a grid of all installed applications, requiring visibility into the complete list of packages installed on the device.

3. **Package Change Monitoring**: The launcher listens for package additions, removals, and updates to keep the app list current.

### Alternatives Considered
- `<queries>` element: Not suitable because launchers need visibility into ALL apps, not a predefined subset.
- Intent-based queries: Insufficient for displaying a complete app catalog.

### Data Handling
- Package information is stored locally on-device only
- No app list data is transmitted to external servers
- Users control which apps appear in their launcher

---

## Network Permissions

### INTERNET & ACCESS_NETWORK_STATE
Used to connect to user-configured RomM server for game library synchronization. No data is sent to third-party servers.
