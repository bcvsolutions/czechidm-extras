# Changelog
All notable changes to this project will be documented in this file.

## [2.1.0]
-  [#1939,#1938,#1869,#1940,#1870] Updated CzechIdMStatusNotificationTask and **template statusNotification**. Please update this template in IdM.

## [2.0.0]

- [#2001](https://redmine.czechidm.com/issues/2001) - Upgraded dependency on idm-core to 10.0.0. Also module was renamed from czechidm-extras to idm-extras, so all dependencies must be changed to be compatible with this change. Module name in ExtrasModuleDescriptor.MODULE_ID remained untouched, so all configuration which uses this (enabling module, etc) should work as before

