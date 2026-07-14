import { LoginForm } from "@/components/login-form";
import { sanitizeReturnTo } from "@/lib/frontend-utils";

interface LoginPageProps {
  searchParams: Promise<{ returnTo?: string | string[] }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const parameters = await searchParams;
  const requestedReturn = Array.isArray(parameters.returnTo)
    ? parameters.returnTo[0]
    : parameters.returnTo;
  return <LoginForm returnTo={sanitizeReturnTo(requestedReturn)} />;
}
