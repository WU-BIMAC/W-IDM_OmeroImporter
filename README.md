# W-IDM_OmeroImporter
This repository contains software to read a folder containing images, read associated metadata csv files and automatically imports the images in an OMERO repository and write those metadata in OMERO key-value pair.  
The additional feature include:  
1) Copy the images and the metadata files in a backup location (either mapped or Backblaze at the moment) and optionally delete the images and metadata files from the origin.  
2) Read Micro-Meta app json metadata files together with the associated images and import them on OMERO as file attachments compatibile with Micro-Meta app OMERO plugin.  
3) For each run create a file with a list of the succesfully imported files, and a file with a list of csv metadata succesfully files written on OMERO.  
4) Use the "previously imported" list to avoid repeating operations in case the files are not removed from the origin folder.  
5) Use the "metadata previously written" list to avoid overriding metadata in case the files are not removed from the origin folder.  
6) For each run create files with errors for debug purposes.  
7) Automatically email the user "owner" of the importing session, if indicated in the parameters, at the end of the importing with relevant data about the process.  
8) Automatically email the user "admin" of the setup, if indicated in the parameters, at the end of the importing with attached all the output file.  


## Installation
To install the application simply download the current release zip file, and unpack it a location of your choice on the system where you plan to run the importing to OMERO. 

## Launch 
The application can be run as via command line, via script or via scheduler / crone job.  
In all case the application can be run using a configuration file or using command line parameters.  
To use the configuration file a folder named OmeroImporter needs to be created under the user folder, and a configuration file named "OmeroImporter.cfg" needs to placed there. 
The configuration file requires to have a parameter per line as the following example: hostname<TABSPACE>omero.server.address.  

## Target folder configuration

The application requires a specific folder structure in the directory that is going to be selected to be imported. 
The current folder structure required is as follows:  
0. Folder to read  
&nbsp;&nbsp;&nbsp;&nbsp;1. Project container folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2a. Project csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2b. Project folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3a. Dataset csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3b. Dataset folder  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;4a. Images csv file  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;4b. Images files  

## Application configuration

The software contains a inbuilt help for command-line run or it can be run using a configuration file to be placed as follows:   userHomeFolder/OmeroImporter/OmeroImporter.cfg  

The current available options are:
CommandLine arg | Config file | Argument | Functionality
----------------|---------------|---------------|---------------
-cfg | | | to run with a configuration file
-h | | | to see help  
-H | hostname | <omero.server.address> | to set the server address  
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


