#!/usr/bin/env bash

set -e

CLI_ERR_MSG="Postgres CLI tools not available (psql). Using Postgres.app, look
at http://postgresapp.com/documentation/cli-tools.html. Aborting."
hash psql 2>/dev/null || { echo >&2 $CLI_ERR_MSG ; exit 1; }


function show_help {
    echo "Takes and optional -f argument to force close client connections."
}

OPTIND=1 # reset
force=0  # default value

while getopts "hf?:" opt; do
    case "$opt" in
        h|\?)
            show_help
            exit 0
            ;;
        f)  force=1
            ;;
    esac
done

shift $((OPTIND-1))

[ "$1" = "--" ] && shift


if [ $force -eq 1 ]; then
   # Drop all connections from lumen user
   psql -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = 'lumen';"
fi


# Clean up test dbs
psql -c "DROP DATABASE IF EXISTS test_lumen_tenant_2;"
psql -c "DROP DATABASE IF EXISTS test_lumen_tenant_1;"
psql -c "DROP DATABASE IF EXISTS test_lumen;"

# Clean up dev dbs
psql -c "DROP DATABASE IF EXISTS lumen_tenant_2;"
psql -c "DROP DATABASE IF EXISTS lumen_tenant_1;"
psql -c "DROP DATABASE IF EXISTS lumen;"
psql -c "DROP USER IF EXISTS lumen;"

echo ""
echo "----------"
echo "Done!"
