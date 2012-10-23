#!/bin/bash

# modify the following to suit your environment
# IMPORTANT: 
# add .my.cnf to the home of the user running this script.
# It must contain the following:
# [client]
# user=[db-user-name]
# pass=[db-user-password]
#

export DB_BACKUP="/home/juxta/sql_backups"
export WEB_DB_NAME="juxta_web_stage"
export WS_DB_NAME="juxta_ws"

# title and version
echo ""
echo "========================="
echo "Backing up MySQL database"
echo "========================="
echo " => Rotating backups..."
rm -rf $DB_BACKUP/5
mv $DB_BACKUP/4 $DB_BACKUP/5 2> /dev/null
mv $DB_BACKUP/3 $DB_BACKUP/4 2> /dev/null
mv $DB_BACKUP/2 $DB_BACKUP/3 2> /dev/null
mv $DB_BACKUP/1 $DB_BACKUP/2 2> /dev/null
mkdir $DB_BACKUP/1 

echo " => Creating WEB backup..."
mysqldump $WEB_DB_NAME | bzip2 > $DB_BACKUP/1/web-`date +%m-%d-%Y`.bz2

echo " => Creating WS backup..."
mysqldump $WS_DB_NAME | bzip2 > $DB_BACKUP/1/ws-`date +%m-%d-%Y`.bz2
echo "========================="
echo "Done"
echo "========================="

exit 0
