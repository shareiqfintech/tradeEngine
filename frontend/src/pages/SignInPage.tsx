import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { TerminalSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { authApi } from '@/api/authApi'
import { ApiError } from '@/api/errors'
import { useAuth } from '@/app/AuthProvider'

export function SignInPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const signInMutation = useMutation({
    mutationFn: () => authApi.signIn({ email, password }),
    onSuccess: (response) => {
      login(response.token, { id: response.id, name: response.name, email: response.email })
      navigate('/dashboard', { replace: true })
    },
    onError: (err) => {
      // Deliberately the same generic message regardless of whether the email
      // exists or the password was wrong - matches the backend's own
      // InvalidCredentialsException behavior.
      setError(err instanceof ApiError ? err.message : 'Invalid email or password')
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    signInMutation.mutate()
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <TerminalSquare className="mb-2 size-8 text-primary" />
          <CardTitle>Trading Engine Console</CardTitle>
          <CardDescription>Sign in to access your dashboard and Groww configuration.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-1.5">
              <Label>Email</Label>
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            </div>
            <div className="space-y-1.5">
              <Label>Password</Label>
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={signInMutation.isPending}>
              {signInMutation.isPending ? 'Signing in…' : 'Sign In'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Don&apos;t have an account?{' '}
            <Link to="/signup" className="font-medium text-primary underline-offset-4 hover:underline">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
