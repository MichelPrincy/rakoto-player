package com.rakotoplayer.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.util.*;

/**
 * Native move/export transactions. No original is removed before the vault is durable and verified.
 */
final class VaultTransfers {
  static final int CONSENT = 108, WRITE = 109;
  private final MainActivity activity;
  private final BrowserScreen screen;
  private final SharedPreferences journal;
  private Boolean consentResult;
  private volatile boolean processingResult;
  private List<MediaEntry> permissionWork;

  VaultTransfers(MainActivity activity, BrowserScreen screen) {
    this.activity = activity;
    this.screen = screen;
    journal = activity.getSharedPreferences("rakoto_vault_transfers", 0);
  }

  void move(List<MediaEntry> entries) {
    if (!screen.active) {
      screen.reload();
      return;
    }
    if (screen.busy) {
      screen.toast("Une opération est déjà en cours.");
      return;
    }
    List<MediaEntry> pending = new ArrayList<>();
    for (MediaEntry e : entries) if (!e.originalUri.isEmpty()) pending.add(e);
    if (pending.isEmpty()) {
      screen.reload();
      return;
    }
    Set<String> workIds = new HashSet<>();
    for (MediaEntry e : pending) workIds.add(e.uri);
    journal.edit().putStringSet("work_ids", workIds).commit();
    if (Build.VERSION.SDK_INT <= 28
        && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      boolean needs = false;
      for (MediaEntry entry : pending) {
        Uri uri = Uri.parse(entry.originalUri);
        if ("media".equals(uri.getAuthority()) || "file".equals(uri.getScheme())) needs = true;
      }
      if (needs) {
        permissionWork = pending;
        activity.vaultWritePermission();
        return;
      }
    }
    screen.job(
        "Déplacement sécurisé",
        () -> {
          Outcome outcome = new Outcome();
          for (MediaEntry entry : pending) {
            if (outcome.request.size() >= 100) break;
            if (!screen.vault.unlocked())
              throw new IOException("Coffre verrouillé. Les originaux non traités sont conservés.");
            try {
              if (journal.getStringSet("deleted_ids", Collections.emptySet()).contains(entry.uri)) {
                finish(entry);
                outcome.done++;
                continue;
              }
              long generation = screen.vault.generation();
              VerifiedRemoval.Ticket verified =
                  VerifiedRemoval.verify(
                      () ->
                          screen.vault.verifyOriginal(
                              entry, (stage, done, total) -> screen.progress(stage, done, total)));
              Uri uri = Uri.parse(entry.originalUri);
              try {
                verified.remove(
                    () -> {
                      if (!screen.vault.unlocked() || generation != screen.vault.generation())
                        throw new IOException("Déplacement interrompu : coffre verrouillé");
                      boolean deleted;
                      if (DocumentsContract.isDocumentUri(activity, uri))
                        deleted =
                            DocumentsContract.deleteDocument(activity.getContentResolver(), uri);
                      else if ("file".equals(uri.getScheme()))
                        deleted = new File(Objects.requireNonNull(uri.getPath())).delete();
                      else deleted = activity.getContentResolver().delete(uri, null, null) > 0;
                      return deleted;
                    },
                    () -> {
                      recordDeleted(entry);
                      finish(entry);
                    });
                outcome.done++;
              } catch (SecurityException denied) {
                Uri media = uri;
                if (Build.VERSION.SDK_INT >= 29 && !"media".equals(media.getAuthority()))
                  try {
                    media = MediaStore.getMediaUri(activity, uri);
                  } catch (Exception ignored) {
                  }
                if (Build.VERSION.SDK_INT >= 30
                    && media != null
                    && "media".equals(media.getAuthority())) {
                  outcome.request.add(media);
                  outcome.entries.add(entry);
                } else if (Build.VERSION.SDK_INT == 29
                    && denied instanceof RecoverableSecurityException) {
                  outcome.sender =
                      ((RecoverableSecurityException) denied)
                          .getUserAction()
                          .getActionIntent()
                          .getIntentSender();
                  outcome.entries.add(entry);
                  break;
                } else throw denied;
              }
            } catch (Exception failure) {
              outcome.errors.add(
                  entry.name
                      + " : "
                      + (failure.getMessage() == null
                          ? "Original conservé"
                          : failure.getMessage()));
            }
          }
          return outcome;
        },
        outcome -> {
          screen.reload();
          if (!outcome.request.isEmpty() || outcome.sender != null) {
            try {
              Set<String> ids = new HashSet<>();
              for (MediaEntry e : outcome.entries) ids.add(e.uri);
              if (!journal
                  .edit()
                  .putStringSet("consent_ids", ids)
                  .putBoolean("consent_deletes", Build.VERSION.SDK_INT >= 30)
                  .commit()) throw new IOException("Enregistrement du transfert impossible");
              IntentSender sender = outcome.sender;
              if (Build.VERSION.SDK_INT >= 30)
                sender =
                    MediaStore.createDeleteRequest(
                            activity.getContentResolver(), new LinkedHashSet<>(outcome.request))
                        .getIntentSender();
              activity.vaultConsent(sender);
            } catch (Exception error) {
              pendingNotice(
                  "La protection est enregistrée, mais Android n’a pas autorisé le retrait des"
                      + " originaux. Utilisez « Terminer le déplacement ».");
            }
          } else if (!outcome.errors.isEmpty())
            pendingNotice(
                "Les fichiers chiffrés sont protégés. Certains déplacements restent à confirmer"
                    + " :\n\n"
                    + android.text.TextUtils.join(
                        "\n", outcome.errors.subList(0, Math.min(4, outcome.errors.size()))));
          else screen.toast(outcome.done + " fichier(s) déplacé(s) dans le coffre");
        },
        true);
  }

  private static final class Outcome {
    int done;
    final List<String> errors = new ArrayList<>();
    final List<Uri> request = new ArrayList<>();
    final List<MediaEntry> entries = new ArrayList<>();
    IntentSender sender;
  }

  private void finish(MediaEntry entry) throws Exception {
    screen.vault.moved(entry);
    screen.library.forget(entry.originalUri);
    Set<String> deleted =
        new HashSet<>(journal.getStringSet("deleted_ids", Collections.emptySet()));
    deleted.remove(entry.uri);
    journal.edit().putStringSet("deleted_ids", deleted).commit();
  }

  private void recordDeleted(MediaEntry entry) throws IOException {
    Set<String> deleted =
        new HashSet<>(journal.getStringSet("deleted_ids", Collections.emptySet()));
    deleted.add(entry.uri);
    if (!journal.edit().putStringSet("deleted_ids", deleted).commit())
      throw new IOException(
          "Suppression confirmée, état du transfert à vérifier au prochain déverrouillage");
  }

  void permission(boolean granted) {
    List<MediaEntry> work = permissionWork;
    permissionWork = null;
    if (granted && work != null) {
      screen.withVault(() -> move(work));
      return;
    }
    pendingNotice(
        "Autorisation refusée : les originaux restent présents. Leur copie chiffrée est conservée"
            + " dans le coffre.");
  }

  void consent(boolean granted) {
    consentResult = granted;
    journal.edit().putBoolean("consent_result", granted).commit();
    resume();
  }

  void resume() {
    if (consentResult == null && journal.contains("consent_result"))
      consentResult = journal.getBoolean("consent_result", false);
    if (consentResult == null
        || !activity.vaultVisible()
        || screen.tab != 2
        || screen.busy
        || processingResult) return;
    boolean granted = consentResult;
    consentResult = null;
    Set<String> ids = new HashSet<>(journal.getStringSet("consent_ids", Collections.emptySet()));
    boolean deleted = journal.getBoolean("consent_deletes", false);
    if (!granted) {
      journal.edit().remove("consent_ids").remove("consent_result").apply();
      pendingNotice(
          "Suppression annulée : les originaux sont conservés. Le coffre indique les déplacements à"
              + " terminer.");
      return;
    }
    screen.withVault(
        () -> {
          processingResult = true;
          screen.job(
              "Finalisation du déplacement",
              () -> {
                try {
                  List<MediaEntry> all = screen.vault.entries(), retry = new ArrayList<>();
                  Set<String> requested = journal.getStringSet("work_ids", ids);
                  for (MediaEntry e : all)
                    if (!e.originalUri.isEmpty()) {
                      if (ids.contains(e.uri) && deleted) {
                        recordDeleted(e);
                        finish(e);
                      } else if (requested.contains(e.uri)) retry.add(e);
                    }
                  return retry;
                } finally {
                  processingResult = false;
                }
              },
              retry -> {
                journal.edit().remove("consent_ids").remove("consent_result").apply();
                screen.reload();
                if (!retry.isEmpty()) move(retry);
                else screen.toast("Originaux retirés de la galerie");
              },
              false);
        });
  }

  private void pendingNotice(String message) {
    if (!screen.destroyed && screen.active)
      screen.showPrivate(
          new AlertDialog.Builder(activity)
              .setTitle("Déplacement à terminer")
              .setMessage(message)
              .setPositiveButton("Compris", null));
  }

  void export(MediaEntry entry) {
    screen.withVault(
        () ->
            screen.showPrivate(
                new AlertDialog.Builder(activity)
                    .setTitle("Restaurer dans la galerie ?")
                    .setMessage(
                        "Le fichier sera déchiffré avec son nom et son extension d’origine, puis"
                            + " retiré du coffre après restauration réussie. Il redeviendra visible"
                            + " aux autres applications.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton(
                        "Restaurer",
                        (d, w) -> {
                          if (Build.VERSION.SDK_INT <= 28
                              && activity.checkSelfPermission(
                                      Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                  != PackageManager.PERMISSION_GRANTED) {
                            pendingExport = entry;
                            activity.vaultWritePermission();
                            return;
                          }
                          exportNow(entry);
                        })));
  }

  private MediaEntry pendingExport;

  boolean exportPermission(boolean granted) {
    if (pendingExport == null) return false;
    MediaEntry entry = pendingExport;
    pendingExport = null;
    if (granted) screen.withVault(() -> exportNow(entry));
    else screen.toast("Export annulé. Le fichier reste dans le coffre.");
    return true;
  }

  private void exportNow(MediaEntry entry) {
    screen.job(
        "Restauration dans la galerie",
        () -> {
          VaultGallery.restore(
              activity,
              screen.vault,
              entry,
              (stage, done, total) -> screen.progress(stage, done, total));
          boolean removed = true;
          try {
            screen.vault.delete(entry);
          } catch (Exception failure) {
            removed = false;
          }
          return removed;
        },
        removed -> {
          screen.reload();
          screen.toast(
              removed
                  ? "Fichier restauré dans la galerie et retiré du coffre"
                  : "Restauration réussie. Le nettoyage du coffre doit être vérifié.");
        },
        true);
  }
}
