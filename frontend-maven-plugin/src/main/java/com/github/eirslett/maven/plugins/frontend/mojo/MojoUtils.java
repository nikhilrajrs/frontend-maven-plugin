package com.github.eirslett.maven.plugins.frontend.mojo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;

class MojoUtils {

    private static enum SCHEMES {
        http, https, ftp
    }

    static <E extends Throwable> MojoFailureException toMojoFailureException(E e) {
        return new MojoFailureException(e.getMessage() + ": " + e.getCause().getMessage(), e);
    }

    static ProxyConfig getProxyConfig(MavenSession mavenSession, SettingsDecrypter decrypter) {
        if ((mavenSession == null) || (mavenSession.getSettings() == null)
                || (mavenSession.getSettings().getProxies() == null)
                || mavenSession.getSettings().getProxies().isEmpty()) {
            return new ProxyConfig(Collections.<ProxyConfig.Proxy> emptyList());
        } else {
            final List<Proxy> mavenProxies = mavenSession.getSettings().getProxies();

            final List<ProxyConfig.Proxy> proxies = new ArrayList<ProxyConfig.Proxy>(mavenProxies.size());

            for (Proxy mavenProxy : mavenProxies) {
                if (mavenProxy.isActive()) {
                    mavenProxy = decryptProxy(mavenProxy, decrypter);
                    for (SCHEMES scheme : SCHEMES.values()) {
                        proxies.add(new ProxyConfig.Proxy(mavenProxy.getId(), scheme.name(), mavenProxy.getHost(),
                                mavenProxy.getPort(), mavenProxy.getUsername(), mavenProxy.getPassword(), mavenProxy
                                        .getNonProxyHosts()));
                    }
                }
            }

            return new ProxyConfig(proxies);
        }
    }

    private static Proxy decryptProxy(Proxy proxy, SettingsDecrypter decrypter) {
        final DefaultSettingsDecryptionRequest decryptionRequest = new DefaultSettingsDecryptionRequest(proxy);
        SettingsDecryptionResult decryptedResult = decrypter.decrypt(decryptionRequest);
        return decryptedResult.getProxy();
    }

    static boolean shouldExecute(BuildContext buildContext, List<File> triggerfiles, File srcdir) {

        // If there is no buildContext, or this is not an incremental build, always execute.
        if ((buildContext == null) || !buildContext.isIncremental()) {
            return true;
        }

        if (triggerfiles != null) {
            for (File triggerfile : triggerfiles) {
                if (buildContext.hasDelta(triggerfile)) {
                    return true;
                }
            }
        }

        if (srcdir == null) {
            return true;
        }

        // Check for changes in the srcdir
        Scanner scanner = buildContext.newScanner(srcdir);
        scanner.scan();
        String[] includedFiles = scanner.getIncludedFiles();
        return ((includedFiles != null) && (includedFiles.length > 0));
    }
}
