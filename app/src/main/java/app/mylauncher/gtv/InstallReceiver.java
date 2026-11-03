package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Gets the result of an update install: opens the system's confirmation screen, or says why it failed. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Toast.makeText(c, R.string.upd_install_failed, Toast.LENGTH_LONG).show();
                }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, R.string.upd_install_failed, Toast.LENGTH_LONG).show();
        }
    }
}
