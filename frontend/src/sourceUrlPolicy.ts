/** Keeps public source links away from credentials and local/private destinations. */
export function safeSourceUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) return null
    const sensitiveParameters = ['token', 'secret', 'password', 'credential', 'authorization',
      'clientid', 'apikey', 'accesskey', 'privatekey', 'clientkey', 'subscriptionkey',
      'signature', 'policy', 'xamzcredential', 'xamzsecuritytoken', 'awsaccesskeyid',
      'assertion', 'bearer', 'session']
    if (Array.from(url.searchParams.keys()).some((key) => {
      const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, '')
      return ['key', 'code', 'auth', 'sig', 'jwt', 'ticket'].includes(normalized)
        || sensitiveParameters.some((sensitive) => normalized.includes(sensitive))
    })) return null
    const hostname = url.hostname.toLowerCase().replace(/\.$/, '').replace(/^\[|\]$/g, '')
    if (!hostname.includes('.') && !hostname.includes(':')) return null
    if (hostname === 'localhost' || hostname.endsWith('.localhost') || hostname.endsWith('.local')
      || hostname === 'internal' || hostname.endsWith('.internal') || hostname.endsWith('.lan')
      || hostname.endsWith('.home') || hostname.endsWith('.home.arpa') || hostname.endsWith('.test')
      || hostname.endsWith('.invalid') || hostname.endsWith('.example')) return null
    const ipv4 = hostname.split('.').map(Number)
    if (ipv4.length === 4 && ipv4.every((part) => Number.isInteger(part) && part >= 0 && part <= 255)) {
      const [first, second, third] = ipv4
      if (first === 0 || first === 10 || first === 127 || first >= 224
        || (first === 169 && second === 254)
        || (first === 172 && second >= 16 && second <= 31)
        || (first === 192 && second === 168)
        || (first === 100 && second >= 64 && second <= 127)
        || (first === 192 && second === 0 && (third === 0 || third === 2))
        || (first === 192 && second === 88 && third === 99)
        || (first === 198 && (second === 18 || second === 19))
        || (first === 198 && second === 51 && third === 100)
        || (first === 203 && second === 0 && third === 113)) return null
    }
    if (hostname.includes(':') && (!/^[23]/i.test(hostname) || /^2001:db8/i.test(hostname)
      || /^2002:/i.test(hostname))) return null
    return url.href
  } catch {
    return null
  }
}
