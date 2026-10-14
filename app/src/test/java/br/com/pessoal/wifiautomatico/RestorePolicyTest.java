// SPDX-License-Identifier: GPL-3.0-only
package br.com.pessoal.wifiautomatico;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RestorePolicyTest {
    @Test public void waitsUntilDeadlineIncludingSleepTime() {
        assertFalse(RestorePolicy.isDue(true, false, 120000, 119999));
        assertTrue(RestorePolicy.isDue(true, false, 120000, 120000));
        assertTrue(RestorePolicy.isDue(true, false, 120000, 900000));
    }
    @Test public void reconnectionOrStoppingPreventsRestore() {
        assertFalse(RestorePolicy.isDue(true, true, 120000, 200000));
        assertFalse(RestorePolicy.isDue(false, false, 120000, 200000));
    }
    @Test public void cancelledAndPreviousBootDeadlinesCannotRestore() {
        assertFalse(RestorePolicy.isDue(true, false, 0, 200000));
        assertFalse(RestorePolicy.isCurrent(true, 0, 4, 4));
    }
    @Test public void staleAlarmCannotRestoreAfterNewDisconnection() {
        assertFalse(RestorePolicy.isCurrent(true, 120000, 5, 4));
        assertTrue(RestorePolicy.isCurrent(true, 120000, 5, 5));
        assertFalse(RestorePolicy.isCurrent(false, 120000, 5, 5));
    }
}
