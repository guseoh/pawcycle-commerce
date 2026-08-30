import { OrbitMark } from "@/components/orbit-mark";
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
  return <div className="authentication-layout"><aside className="authentication-brand" aria-label="PawCycle"><span>PAW / CYCLE</span><h2>함께 사는 일상,<br />다시 만나는 순간.</h2><OrbitMark size={160} /><p>필요한 만큼, 편하게.</p></aside><LoginForm returnTo={sanitizeReturnTo(requestedReturn)} /></div>;
}
