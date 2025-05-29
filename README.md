# APP-Metabuild
 An universal build management tool for Java and C/C++ (additonal trough plugins or future changes possible)

 Oriented on the gradle system, but with some significant changes:
 - buildfile written in Java
 - import of other buildfiles in arbitary order and at arbitary location within the buildfile
 - possibility to directly declare tasks in other projects as dependencies of other tasks
 - "wrapper" instance in the project contains all installation files, no internet connectio required for installing to new system

 Some limitations still exist, the projects is still in-dev but already mostely functional for Java.
