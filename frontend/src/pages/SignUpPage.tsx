import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { TerminalSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { authApi } from '@/api/authApi'
import { ApiError } from '@/api/errors'
import { toast } from '@/hooks/useToast'

export function SignUpPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const signUpMutation = useMutation({
    mutationFn: () => authApi.signUp({ name, email, password, confirmPassword }),
    onSuccess: () => {
      toast.success('Account created', 'You can now sign in with your new account.')
      navigate('/signin', { replace: true })
    },
    onError: (error) => {
      setFieldErrors(error instanceof ApiError && error.fieldErrors ? error.fieldErrors : {})
      const message = error instanceof ApiError ? error.message : 'Unknown error'
      toast.error('Could not create account', message)
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFieldErrors({})
    signUpMutation.mutate()
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <TerminalSquare className="mb-2 size-8 text-primary" />
          <CardTitle>Create your account</CardTitle>
          <CardDescription>Sign up to configure your Groww connection and access the console.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <Field label="Name" error={fieldErrors.name}>
              <Input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
            </Field>
            <Field label="Email" error={fieldErrors.email}>
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            </Field>
            <Field label="Password" error={fieldErrors.password}>
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            </Field>
            <Field label="Confirm Password" error={fieldErrors.confirmPassword ?? fieldErrors.passwordConfirmed}>
              <Input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            </Field>
            <Button type="submit" className="w-full" disabled={signUpMutation.isPending}>
              {signUpMutation.isPending ? 'Creating account…' : 'Sign Up'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/signin" className="font-medium text-primary underline-offset-4 hover:underline">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
