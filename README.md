# W-IDM_OmeroImporter
This repository contains software to read a folder containing images, read associated metadata csv files and automatically imports the images in an OMERO repository and write those metadata in OMERO key-value pair.  
Additionally it is possible to use it to copy the images in a backup location OR a Backblaze backup location.

## Installation
TODO

## Launch

## Configuration

The application requires a specific folder structure in the directory that is going to be selected The current folder structure required is as follows:  
0. Folder to read  
&nbsp;&nbsp;&nbsp;&nbsp;1. Project container folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2a. Project csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2b. Project folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3a. Dataset csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3b. Dataset folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;4a. Images csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;4b. Images files  

The software contains a inbuilt help for command-line run or it can be run using a configuration file to be placed as follows:   userHomeFolder/OmeroImporter/OmeroImporter.cfg  

The current available options are:
CommandLine arg | Config file | Argument | Functionality
----------------|---------------|---------------|---------------
-cfg | | | to run with a configuration file
-h | | | to see help  
-H | hostname| <omero.server.address> | to set the server address  
-P | port | <omero.server.port> | to set the server port  
-u | username | <omero.server.username> | to set the server username  
-p | password | <omero.server.password> | to set the server password 
-t | target | <path/to/images/folder> | to set the folder to read, containing images and csv files as specified above  
-d | destination | <path/to/backup/folder> | to set the folder where to copy the images and csv files after import, if using backblaze it adds the path in the bucket  
-hl | headless | <true/false> (only in config) | to set if the application is running headless, avoiding to prompt to user for warning  
-del | delete | <true/false> (only in config) | to delete the files from the original location after copy (doesn't work if using backblaze)  
-mma | mma | <true/false> (only in config) | to automatically import micro-meta-app files associated with images  
-b2 | b2 | <KeyID:AppID:BucketName> | to use backblaze as destination for backup copy  
-ml | email | <user.email.com> | to email the user once the process is completed  
-aml | admin-email | <admin.email.com> | to email the admin a copy of the output and log files once the process is completed  
-tf | timeframe | <<timeStart:timeEnd>> | to specify a timeframe in which the application should run (this forces the application to exit once outside the timeframe

NB: in configuration file the option name and the argument needs to be divided by a TAB space (\t).  

