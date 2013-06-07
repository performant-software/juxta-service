#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ "$#" -eq 0 ] 
then
	cmd="mysql"
else
	if [ "$#" -lt 2 ]
	then
		cmd="mysql -u $1"
	else
		cmd="mysql -u $1 -p $2"
	fi
fi

echo "Initializing JuxtaWS Schema..."
eval "$cmd < $DIR/create_db.sql"

echo "Creating JuxtaWS MySQL user..."
eval "$cmd < $DIR/grant.sql"

echo "Seeding juxta_ws database..."
eval "$cmd < $DIR/seed_db.sql"

echo "JuxtaWS database is ready"
