import React, { PropTypes } from 'react';
import { Link } from 'react-router';

export default function NavWorkspaceSwitch({ profile }) {
  return (
    <div className="NavWorkspaceSwitch">
      {profile.admin && <Link to="/admin/users">Admin view</Link>}
    </div>
  );
}

