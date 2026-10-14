// SPDX-License-Identifier: GPL-3.0-only
package br.com.pessoal.wifiautomatico;

final class RestorePolicy {
    static boolean isCurrent(boolean enabled, long deadline, long currentToken, long incomingToken) {
        return enabled && deadline > 0 && currentToken == incomingToken;
    }

    static boolean isDue(boolean enabled, boolean wifi, long deadline, long now) {
        return enabled && !wifi && deadline > 0 && now >= deadline;
    }
}
