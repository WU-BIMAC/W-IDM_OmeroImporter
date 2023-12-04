# W-IDM_OmeroImporter
The OMEROImporter (Automated Image Data and Metadata Annotation Importer) automatically imports image data from acquisition storage to a repository, and annotates such images with relevant sample preparation and microscopy metadata as follows:
1) Sample Metadata is imported from CVS files exported from customized metadata-collection spreadsheet templates (i.e., xcel, google sheet, etc.,) and written as key/value or tag unstructured annotations in OMERO
2) Microscopy metadata is imported from Microscope.JSON files produced using the Micro-Meta App (https://github.com/WU-BIMAC/MicroMetaApp-Electron) and attached to the images to be viewed using the Micro-Meta App OMERO-plugin. (https://github.com/WU-BIMAC/MicroMetaApp-Omero)

Additional features include:  
1) Copy the images and the metadata files in a backup location and optionally delete the images and metadata files from the origin.  
3) Create a log file with a list of the successfully imported files and a file with a list of CSV metadata files successfully written on OMERO.  
4) Create an error report file for debugging purposes.  
5) Use the "previously imported" list to avoid duplicate importing.
6) Use the "metadata previously written" list to override metadata annotations.  
7) Automatically email a report to the user of the importing session, if indicated.  
8) Automatically email the admin of the importing session, if indicated/

## Installation
To install the application simply download the current release zip file, and unpack it a location of your choice on the system where you plan to run the sofware. 

## Launch 
The application can be run as via command line, via script or via scheduler / crone job.  
In all cases the application can be run using a configuration file or using command line parameters.  
To use the configuration file, a folder named OmeroImporter needs to be created under the user folder, and a configuration file named "OmeroImporter.cfg" needs to placed there. 
The configuration file requires a parameter per line as the following example: hostname<TABSPACE>omero.server.address.  

## Target folder configuration

The application requires a specific folder structure in the target directory that is going to be selected for import. 
The required folder structure is as follows:  

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


