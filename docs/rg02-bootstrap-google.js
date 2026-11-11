const button = document.getElementById("connect");
  const result = document.getElementById("result");
  if (location.hostname !== "robdor80.github.io" ||
      location.pathname !== "/RobGuard/rg02-bootstrap-google.html" ||
      location.protocol !== "https:") {
    result.textContent = "Por seguridad, abre esta página desde GitHub Pages oficial.";
  } else {
    try {
      const [{ initializeApp }, firebaseAuth] = await Promise.all([
        import("https://www.gstatic.com/firebasejs/12.0.0/firebase-app.js"),
        import("https://www.gstatic.com/firebasejs/12.0.0/firebase-auth.js")
      ]);
      const app = initializeApp({
        apiKey: "AIzaSyCOnLVJCDcwed0YVfRYgClQjexjebBHS1Q",
        authDomain: "robguard-ed719.firebaseapp.com",
        projectId: "robguard-ed719"
      });
      const auth = firebaseAuth.getAuth(app);
      await firebaseAuth.setPersistence(auth, firebaseAuth.inMemoryPersistence);
      button.disabled = false;
      result.textContent = "Preparado. No se ha iniciado ninguna sesión.";
      button.addEventListener("click", async () => {
        button.disabled = true;
        result.textContent = "Esperando autenticación Google…";
        try {
          const provider = new firebaseAuth.GoogleAuthProvider();
          provider.setCustomParameters({prompt: "select_account"});
          const credential = await firebaseAuth.signInWithPopup(auth, provider);
          const uid = credential.user.uid;
          const email = credential.user.email || "(correo no disponible)";
          await firebaseAuth.signOut(auth);
          result.replaceChildren();
          const account = document.createElement("p");
          account.textContent = "Cuenta autenticada: " + email;
          result.appendChild(account);
          const uidNode = document.createElement("p");
          uidNode.append("UID de Firebase: ");
          const code = document.createElement("code");
          code.textContent = uid;
          uidNode.append(code);
          result.appendChild(uidNode);
          const copy = document.createElement("button");
          copy.type = "button";
          copy.textContent = "Copiar UID";
          copy.addEventListener("click", () => navigator.clipboard.writeText(uid));
          result.appendChild(copy);
          const explanation = document.createElement("p");
          explanation.textContent = "La sesión se ha cerrado. Comprueba el correo y ve a Firebase Authentication → Usuarios para verificar el registro.";
          result.appendChild(explanation);
        } catch (e) {
          result.textContent = "No se completó el acceso. Código: " + (e?.code || "desconocido") +
            ". Detalle: " + (e?.message || "sin detalles") +
            ". Si vuelve a ocurrir, comparte una captura de esta pantalla (sin credenciales).";
        } finally { button.disabled = false; }
      });
    } catch (e) {
      result.textContent = "No se pudo preparar la autenticación web. Comprueba la conexión y abre la página desde Chrome.";
    }
  }
