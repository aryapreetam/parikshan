# GitHub Secrets Setup Guide

This guide walks you through setting up all required GitHub secrets for automated publishing to Maven Central.

---

## 🔐 Required Secrets

Your CI/CD pipeline requires 5 secrets to publish to Maven Central:

| Secret Name | Purpose | Example |
|------------|---------|---------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype account username | `john.doe` |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype account password/token | `MySecureP@ssword123` |
| `SIGNING_KEY_ID` | Last 8 chars of GPG key ID | `ABCD1234` |
| `SIGNING_PASSWORD` | GPG key passphrase | `MyGPGPassphrase` |
| `GPG_KEY_CONTENTS` | ASCII-armored private GPG key | `-----BEGIN PGP PRIVATE...` |

---

## Step 1: Create Sonatype Account

### 1.1 Sign Up

1. Go to https://central.sonatype.com/
2. Click "Sign up" (top right)
3. Create an account with username and password
4. Verify your email address

### 1.2 Create Namespace

1. After login, click your profile → "View Namespaces"
2. Click "Add Namespace"
3. For GitHub-based publishing, use: `io.github.yourusername`
   - Replace `yourusername` with your **actual GitHub username**
4. Choose verification method: **GitHub Repository**
5. Follow instructions to verify:
   - Create a public repository named exactly as instructed (e.g., `OSSRH-12345`)
   - Or add the verification code to your repo description
6. Click "Verify Namespace"
7. Wait for approval (usually instant for GitHub verification)

### 1.3 Get Credentials

Once namespace is verified:

- **Username**: Your Sonatype username (what you signed up with)
- **Password**: Your Sonatype password

**Recommended: Use Token Instead of Password**

1. Go to your profile → "Generate User Token"
2. Click "Generate"
3. Save the token username and token password
4. Use these instead of your actual credentials

---

## Step 2: Generate GPG Key

### 2.1 Check for Existing Keys

```bash
gpg --list-secret-keys --keyid-format=long
```

If you already have a key, you can skip to step 2.3.

### 2.2 Generate New Key

```bash
# Start key generation
gpg --full-generate-key
```

Follow the prompts:

1. **Key type**: Choose `(1) RSA and RSA` (default)
2. **Key size**: Enter `4096`
3. **Expiration**: Enter `0` (no expiration) or set expiration as desired
4. **Confirm**: Enter `y`
5. **Real name**: Enter your full name (e.g., `John Doe`)
6. **Email**: Enter your email (e.g., `john.doe@example.com`)
7. **Comment**: Leave empty or add comment
8. **Confirm**: Enter `O` (okay)
9. **Passphrase**: Enter a strong passphrase (you'll need this later)
10. **Confirm passphrase**: Re-enter the same passphrase

**Save your passphrase securely!** You'll need it for the `SIGNING_PASSWORD` secret.

### 2.3 Find Your Key ID

```bash
gpg --list-secret-keys --keyid-format=long
```

Output looks like:
```
/Users/yourname/.gnupg/secring.gpg
-----------------------------------
sec   rsa4096/ABCD1234EFGH5678 2025-01-01 [SC]
      1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234
uid                 [ultimate] John Doe <john.doe@example.com>
ssb   rsa4096/0987654321FEDCBA 2025-01-01 [E]
```

**Your key ID** is the part after `rsa4096/` on the `sec` line: `ABCD1234EFGH5678`

For the `SIGNING_KEY_ID` secret, you need the **last 8 characters**: `EFGH5678`

### 2.4 Export Private Key (ASCII-armored)

```bash
# Replace ABCD1234EFGH5678 with your actual key ID
gpg --export-secret-keys --armor ABCD1234EFGH5678 > private-key.asc
```

This creates `private-key.asc` containing your private key in ASCII format.

**⚠️ IMPORTANT**: This file contains your private key! Keep it secure and never commit it to Git.

### 2.5 Upload Public Key to Keyserver

Maven Central requires your public key to be available on a keyserver:

```bash
# Replace ABCD1234EFGH5678 with your actual key ID
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678
```

Wait a few minutes, then verify it's uploaded:

```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys ABCD1234EFGH5678
```

You should see: "key ABCD1234EFGH5678: "John Doe <john.doe@example.com>" not changed"

**Alternative keyservers** (if ubuntu keyserver is down):
```bash
gpg --keyserver keys.openpgp.org --send-keys ABCD1234EFGH5678
# or
gpg --keyserver pgp.mit.edu --send-keys ABCD1234EFGH5678
```

---

## Step 3: Add Secrets to GitHub

### 3.1 Navigate to Secrets

1. Go to your GitHub repository
2. Click **Settings** (top navigation)
3. Click **Secrets and variables** (left sidebar)
4. Click **Actions**
5. Click **New repository secret** (green button)

### 3.2 Add Each Secret

Add the following 5 secrets one by one:

#### Secret 1: MAVEN_CENTRAL_USERNAME

- **Name**: `MAVEN_CENTRAL_USERNAME`
- **Value**: Your Sonatype username (or token username if using token)
- Click "Add secret"

#### Secret 2: MAVEN_CENTRAL_PASSWORD

- **Name**: `MAVEN_CENTRAL_PASSWORD`
- **Value**: Your Sonatype password (or token password if using token)
- Click "Add secret"

#### Secret 3: SIGNING_KEY_ID

- **Name**: `SIGNING_KEY_ID`
- **Value**: Last 8 characters of your GPG key ID (e.g., `EFGH5678`)
- Click "Add secret"

#### Secret 4: SIGNING_PASSWORD

- **Name**: `SIGNING_PASSWORD`
- **Value**: The passphrase you entered when creating the GPG key
- Click "Add secret"

#### Secret 5: GPG_KEY_CONTENTS

- **Name**: `GPG_KEY_CONTENTS`
- **Value**: Contents of the `private-key.asc` file

To get the value:
```bash
cat private-key.asc
```

Copy the **entire output** including the BEGIN and END lines:
```
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQdGBGX...
[many lines of base64-encoded content]
...XYZ==
-----END PGP PRIVATE KEY BLOCK-----
```

- Paste this into the secret value field
- Click "Add secret"

### 3.3 Verify All Secrets

After adding all secrets, you should see 5 secrets listed:
- ✅ GPG_KEY_CONTENTS
- ✅ MAVEN_CENTRAL_PASSWORD
- ✅ MAVEN_CENTRAL_USERNAME
- ✅ SIGNING_KEY_ID
- ✅ SIGNING_PASSWORD

---

## Step 4: Test the Setup

### 4.1 Local Publishing Test (Optional)

Before testing in CI, you can test publishing locally:

Add to `~/.gradle/gradle.properties`:
```properties
signing.keyId=EFGH5678
signing.password=YourGPGPassphrase
signing.secretKeyRingFile=/Users/yourname/.gnupg/secring.gpg

mavenCentralUsername=your-sonatype-username
mavenCentralPassword=your-sonatype-password
```

Then test:
```bash
./gradlew :lib:publishToMavenLocal
```

### 4.2 CI Publishing Test

Create a test release:

```bash
# Ensure your code is committed
git add .
git commit -m "Test release setup"
git push

# Create and push a test tag
git tag v0.0.1-test
git push origin v0.0.1-test
```

Monitor the workflow:
1. Go to **Actions** tab
2. Watch "Publish Multiplatform Release" workflow
3. Check each job passes

If the `publish-to-maven-central` job fails, check:
- All 5 secrets are correctly set
- GPG key was uploaded to keyserver
- Sonatype namespace is verified
- Version number is unique (not already published)

---

## 🔧 Troubleshooting

### Issue: "Could not find io.github.yourname:yourlibname"

**Cause**: Namespace not verified in Sonatype

**Solution**: 
1. Log into https://central.sonatype.com/
2. Go to your namespaces
3. Verify your `io.github.yourname` namespace

### Issue: "No public key found"

**Cause**: GPG public key not on keyserver

**Solution**:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Wait 5-10 minutes for keyserver sync.

### Issue: "Wrong passphrase"

**Cause**: `SIGNING_PASSWORD` doesn't match your GPG key passphrase

**Solution**:
1. Test your passphrase locally:
   ```bash
   echo "test" | gpg --clearsign --local-user YOUR_KEY_ID
   ```
2. If it works, update the `SIGNING_PASSWORD` secret with exact passphrase

### Issue: "401 Unauthorized" when publishing

**Cause**: Wrong Sonatype credentials

**Solution**:
1. Verify credentials at https://central.sonatype.com/
2. Update `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`
3. Consider using token instead of password

### Issue: "ASCII armor corrupted"

**Cause**: `GPG_KEY_CONTENTS` was not copied correctly

**Solution**:
1. Re-export the key:
   ```bash
   gpg --export-secret-keys --armor YOUR_KEY_ID > private-key.asc
   ```
2. Copy the ENTIRE file content (including BEGIN/END lines)
3. Update the `GPG_KEY_CONTENTS` secret

---

## 🔒 Security Best Practices

1. **Never commit secrets to Git**
   - Don't commit `private-key.asc` or `gradle.properties` with credentials
   - Add them to `.gitignore`

2. **Use tokens instead of passwords**
   - Sonatype tokens can be revoked without changing your password
   - More secure for CI/CD

3. **Rotate keys periodically**
   - Update GPG keys and passwords every 1-2 years
   - Update GitHub secrets when rotating

4. **Limit secret access**
   - Only grant repository access to trusted collaborators
   - Use GitHub environments for additional protection

5. **Backup your GPG key**
   - Save `private-key.asc` to a secure location
   - If you lose it, you can't sign with the same key

---

## ✅ Checklist

Before creating your first release, ensure:

- [ ] Sonatype account created and verified
- [ ] Namespace verified (io.github.yourname)
- [ ] GPG key generated
- [ ] GPG key uploaded to keyserver
- [ ] All 5 GitHub secrets added
- [ ] Secrets tested (optional: local publish)
- [ ] Test release created successfully

---

## 📚 Additional Resources

- [Maven Central Publishing Guide](https://central.sonatype.org/publish/publish-guide/)
- [GPG Documentation](https://www.gnupg.org/documentation/)
- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Kotlin Multiplatform Publishing](https://kotlinlang.org/docs/multiplatform-publish-libraries.html)

