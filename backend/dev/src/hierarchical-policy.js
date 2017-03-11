// Given a uri like /tenant/1/team/2/folder/4 it will grant access if
// the user has one of the following roles:
//      * tenant:1:team:2:folder:4
//      * tenant:1:team:2
//      * tenant:1
var uri = $evaluation.getPermission().getResource().getUri();
print("Starting for " + uri);
var identity = $evaluation.getContext().getIdentity();
var role = uri.substring(1).replace(/\//g, ":");

var role_found = false;

do {
    role_found = identity.hasRealmRole(role);
    role = role.substring(0, role.lastIndexOf(":", role.lastIndexOf(":") - 1));
} while (!role_found && role.indexOf(":") != -1);

print("Found? " + role_found + " for " + uri);

if (role_found) {
    $evaluation.grant();
} else {
    $evaluation.deny();
}