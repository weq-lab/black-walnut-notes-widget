package com.blackwalnut.noteswidget;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Base64;

import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.security.SecureRandom;

final class FirebaseAuthController {
    interface Callback {
        void onComplete(boolean success, String message);
    }

    static boolean isConfigured(Context context) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return false;
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context);
        return !FirebaseApp.getApps(context).isEmpty();
    }

    static FirebaseUser currentUser(Context context) {
        if (!isConfigured(context)) return null;
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    static String currentUid(Context context) {
        FirebaseUser user = currentUser(context);
        return user == null ? "" : user.getUid();
    }

    static void signIn(Activity activity, Callback callback) {
        if (!isConfigured(activity)) {
            callback.onComplete(false, "Firebase 설정이 없어 로컬 모드로 실행 중입니다.");
            return;
        }
        int clientIdResource = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        if (clientIdResource == 0) {
            callback.onComplete(false, "google-services.json에 웹 OAuth 클라이언트 ID가 없습니다.");
            return;
        }
        String serverClientId = activity.getString(clientIdResource);
        GetSignInWithGoogleOption googleOption = new GetSignInWithGoogleOption.Builder(serverClientId)
                .setNonce(randomNonce())
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleCredential(activity, response.getCredential(), callback);
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        callback.onComplete(false, "Google 로그인 취소 또는 실패: " + safeMessage(error));
                    }
                }
        );
    }

    private static void handleCredential(Activity activity, Credential credential, Callback callback) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            callback.onComplete(false, "지원하지 않는 로그인 응답입니다.");
            return;
        }
        try {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(google.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                    .addOnCompleteListener(activity, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            callback.onComplete(true, user == null ? "로그인됨" : user.getEmail() + " 로그인됨");
                        } else {
                            callback.onComplete(false, "Firebase 인증 실패: " + safeMessage(task.getException()));
                        }
                    });
        } catch (Exception error) {
            callback.onComplete(false, "Google ID 토큰을 처리하지 못했습니다: " + safeMessage(error));
        }
    }

    static void signOut(Activity activity, Callback callback) {
        if (!isConfigured(activity)) {
            callback.onComplete(true, "로컬 모드");
            return;
        }
        FirestoreSyncManager.stop();
        FirebaseAuth.getInstance().signOut();
        CredentialManager.create(activity).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                activity.getMainExecutor(),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { callback.onComplete(true, "로그아웃됨 · 로컬 캐시는 유지됩니다."); }
                    @Override public void onError(ClearCredentialException error) { callback.onComplete(true, "로그아웃됨 · 로컬 캐시는 유지됩니다."); }
                }
        );
    }

    private static String randomNonce() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) return "알 수 없는 오류";
        return error.getMessage();
    }

    private FirebaseAuthController() { }
}
