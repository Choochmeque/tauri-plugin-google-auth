<script>
  import { signIn, signOut, refreshToken } from '@choochmeque/tauri-plugin-google-auth-api'

  // State
  let tokens = $state(null)
  let loading = $state(false)
  let error = $state(null)
  let logs = $state([])

  // Config - replace with your own values
  let clientId = $state('')
  let clientSecret = $state('')
  let scopes = $state('openid,email,profile')
  let flowType = $state('native')

  function log(message, data = null) {
    const entry = {
      time: new Date().toLocaleTimeString(),
      message,
      data: data ? JSON.stringify(data, null, 2) : null
    }
    logs = [entry, ...logs].slice(0, 50)
  }

  async function handleSignIn() {
    if (!clientId) {
      error = 'Client ID is required'
      return
    }

    loading = true
    error = null

    try {
      log('Starting sign-in...')
      const result = await signIn({
        clientId,
        clientSecret: clientSecret || undefined,
        scopes: scopes.split(',').map(s => s.trim()).filter(Boolean),
        flowType
      })
      tokens = result
      log('Sign-in successful', result)
    } catch (e) {
      error = String(e)
      log('Sign-in failed', { error: String(e) })
    } finally {
      loading = false
    }
  }

  async function handleSignOut() {
    loading = true
    error = null

    try {
      log('Signing out...')
      await signOut({
        accessToken: tokens?.accessToken,
        flowType
      })
      tokens = null
      log('Sign-out successful')
    } catch (e) {
      error = String(e)
      log('Sign-out failed', { error: String(e) })
    } finally {
      loading = false
    }
  }

  async function handleRefreshToken() {
    if (flowType === 'web' && !tokens?.refreshToken) {
      error = 'No refresh token available'
      return
    }

    loading = true
    error = null

    try {
      log('Refreshing token...')
      const result = await refreshToken({
        refreshToken: tokens?.refreshToken || undefined,
        clientId,
        clientSecret: clientSecret || undefined,
        scopes: scopes.split(',').map(s => s.trim()).filter(Boolean),
        flowType
      })
      tokens = result
      log('Token refreshed', result)
    } catch (e) {
      error = String(e)
      log('Token refresh failed', { error: String(e) })
    } finally {
      loading = false
    }
  }

  function formatExpiry(expiresAt) {
    if (!expiresAt) return 'N/A'
    const date = new Date(expiresAt * 1000)
    const now = new Date()
    const diff = date - now
    const minutes = Math.floor(diff / 60000)
    return `${date.toLocaleTimeString()} (${minutes}m remaining)`
  }

  function clearLogs() {
    logs = []
  }
</script>

<main class="container">
  <h1>Google Auth Demo</h1>

  <!-- Configuration -->
  <section class="config">
    <h2>Configuration</h2>
    <div class="form-group">
      <label for="clientId">Client ID</label>
      <input
        id="clientId"
        type="text"
        bind:value={clientId}
        placeholder="your-client-id.apps.googleusercontent.com"
      />
    </div>
    <div class="form-group">
      <label for="clientSecret">Client Secret (desktop only)</label>
      <input
        id="clientSecret"
        type="password"
        bind:value={clientSecret}
        placeholder="your-client-secret"
      />
    </div>
    <div class="form-group">
      <label for="scopes">Scopes (comma-separated)</label>
      <input
        id="scopes"
        type="text"
        bind:value={scopes}
        placeholder="openid,email,profile"
      />
    </div>
    <div class="form-group">
      <label for="flowType">Flow Type (Android only)</label>
      <select id="flowType" bind:value={flowType}>
        <option value="native">native</option>
        <option value="web">web</option>
      </select>
    </div>
  </section>

  <!-- Actions -->
  <section class="actions">
    <h2>Actions</h2>
    <div class="button-group">
      <button onclick={handleSignIn} disabled={loading || !clientId}>
        {loading ? 'Loading...' : 'Sign In'}
      </button>
      <button onclick={handleSignOut} disabled={loading || !tokens}>
        Sign Out
      </button>
      <button onclick={handleRefreshToken} disabled={loading || !tokens || (flowType === 'web' && !tokens?.refreshToken)}>
        Refresh Token
      </button>
    </div>
  </section>

  <!-- Error -->
  {#if error}
    <div class="error">{error}</div>
  {/if}

  <!-- Token Info -->
  {#if tokens}
    <section class="tokens">
      <h2>Tokens</h2>
      <div class="token-grid">
        <div class="token-item">
          <strong>Access Token</strong>
          <code>{tokens.accessToken?.slice(0, 50)}...</code>
        </div>
        {#if tokens.idToken}
          <div class="token-item">
            <strong>ID Token</strong>
            <code>{tokens.idToken?.slice(0, 50)}...</code>
          </div>
        {/if}
        {#if tokens.refreshToken}
          <div class="token-item">
            <strong>Refresh Token</strong>
            <code>{tokens.refreshToken?.slice(0, 50)}...</code>
          </div>
        {/if}
        <div class="token-item">
          <strong>Scopes</strong>
          <code>{tokens.scopes?.join(', ') || 'N/A'}</code>
        </div>
        <div class="token-item">
          <strong>Expires</strong>
          <code>{formatExpiry(tokens.expiresAt)}</code>
        </div>
      </div>
    </section>
  {/if}

  <!-- Logs -->
  <section class="logs">
    <div class="logs-header">
      <h2>Logs</h2>
      <button class="clear-btn" onclick={clearLogs}>Clear</button>
    </div>
    <div class="log-container">
      {#each logs as entry}
        <div class="log-entry">
          <span class="log-time">[{entry.time}]</span>
          <span class="log-message">{entry.message}</span>
          {#if entry.data}
            <pre class="log-data">{entry.data}</pre>
          {/if}
        </div>
      {/each}
      {#if logs.length === 0}
        <div class="log-empty">No logs yet</div>
      {/if}
    </div>
  </section>
</main>

<style>
  .container {
    max-width: 800px;
    margin: 0 auto;
    padding: 20px;
  }

  h1 {
    margin-bottom: 24px;
  }

  h2 {
    font-size: 1.2em;
    margin-bottom: 12px;
    color: #666;
  }

  section {
    margin-bottom: 24px;
    padding: 16px;
    background: rgba(0, 0, 0, 0.05);
    border-radius: 8px;
  }

  .form-group {
    margin-bottom: 12px;
  }

  .form-group label {
    display: block;
    margin-bottom: 4px;
    font-size: 0.9em;
    color: #666;
  }

  .form-group input,
  .form-group select {
    width: 100%;
    box-sizing: border-box;
    padding: 8px 12px;
    font-size: 1em;
    border: 1px solid #ccc;
    border-radius: 4px;
    background: #fff;
  }

  .form-group select {
    cursor: pointer;
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 12px center;
    padding-right: 36px;
  }

  .button-group {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .error {
    padding: 12px;
    background: #ffebee;
    color: #c62828;
    border-radius: 8px;
    margin-bottom: 16px;
  }

  .token-grid {
    display: grid;
    gap: 12px;
  }

  .token-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .token-item strong {
    font-size: 0.85em;
    color: #666;
  }

  .token-item code {
    font-size: 0.8em;
    background: rgba(0, 0, 0, 0.1);
    padding: 8px;
    border-radius: 4px;
    word-break: break-all;
  }

  .logs-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .clear-btn {
    padding: 4px 12px;
    font-size: 0.85em;
  }

  .log-container {
    max-height: 300px;
    overflow-y: auto;
    font-family: monospace;
    font-size: 0.85em;
  }

  .log-entry {
    padding: 8px 0;
    border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  }

  .log-time {
    color: #888;
    margin-right: 8px;
  }

  .log-data {
    margin: 8px 0 0 0;
    padding: 8px;
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
    overflow-x: auto;
    font-size: 0.9em;
  }

  .log-empty {
    color: #888;
    text-align: center;
    padding: 20px;
  }

  @media (prefers-color-scheme: dark) {
    h2 {
      color: #aaa;
    }

    .form-group label {
      color: #aaa;
    }

    .form-group input,
    .form-group select {
      background: #2a2a2a;
      border-color: #444;
      color: #eee;
    }

    .form-group select {
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23aaa' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
    }

    section {
      background: rgba(255, 255, 255, 0.05);
    }

    .error {
      background: #4a1515;
      color: #ff8a80;
    }

    .token-item strong {
      color: #aaa;
    }

    .token-item code {
      background: rgba(255, 255, 255, 0.1);
    }

    .log-entry {
      border-bottom-color: rgba(255, 255, 255, 0.1);
    }

    .log-data {
      background: rgba(255, 255, 255, 0.1);
    }
  }
</style>
