"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useRef, useState } from "react";
import { LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";

type LoginErrors = Partial<Record<"email" | "password", string>>;

export function LoginForm({ returnTo }: { returnTo: string }) {
  const router = useRouter();
  const auth = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<LoginErrors>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const nextErrors: LoginErrors = {};
    if (!email.trim()) nextErrors.email = "이메일을 입력해 주세요.";
    if (!password) nextErrors.password = "비밀번호를 입력해 주세요.";
    setErrors(nextErrors);
    setMessage(null);
    if (Object.keys(nextErrors).length > 0) {
      requestAnimationFrame(() => errorRef.current?.focus());
      return;
    }

    setSubmitting(true);
    try {
      await auth.login(email, password);
      router.replace(returnTo);
    } catch (error) {
      if (error instanceof CsrfRefreshError) {
        setMessage("보안 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      } else if (error instanceof ApiError) {
        const fieldErrors: LoginErrors = {};
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === "email" || fieldError.field === "password") {
            fieldErrors[fieldError.field] = fieldError.message;
          }
        }
        setErrors(fieldErrors);
        if (error.code === "CSRF_INVALID") {
          setMessage("보안 정보를 새로 받았습니다. 비밀번호를 확인한 뒤 로그인을 다시 눌러 주세요.");
        } else if (error.code === "INVALID_CREDENTIALS") {
          setMessage("이메일 또는 비밀번호를 확인해 주세요.");
        } else {
          setMessage(error.message);
        }
      } else {
        setMessage("로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  if (auth.status === "loading") {
    return <LoadingState>로그인 상태와 보안 정보를 확인하고 있습니다.</LoadingState>;
  }

  if (auth.status === "authenticated") {
    return (
      <section className="section-card login-card">
        <p className="eyebrow">Signed in</p>
        <h1>이미 로그인되어 있습니다.</h1>
        <p>요청한 화면으로 계속 이동할 수 있습니다.</p>
        {message ? (
          <div className="error-summary" ref={errorRef} tabIndex={-1} role="alert">
            <p>{message}</p>
          </div>
        ) : null}
        <div className="button-row">
          <button className="button button-primary" type="button" onClick={() => router.replace(returnTo)}>
            계속하기
          </button>
          <Link className="button button-secondary" href="/products">상품 목록</Link>
        </div>
      </section>
    );
  }

  return (
    <section className="section-card login-card" aria-labelledby="login-title">
      <p className="eyebrow">Member access</p>
      <h1 id="login-title">로그인</h1>
      <p>구독을 만들거나 내 구독을 확인하려면 로그인이 필요합니다.</p>

      {(message || Object.values(errors).some(Boolean)) ? (
        <div className="error-summary" ref={errorRef} tabIndex={-1} role="alert">
          <h2>로그인 정보를 확인해 주세요.</h2>
          {message ? <p>{message}</p> : null}
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label className="field-label" htmlFor="email">이메일</label>
          <input
            className="input"
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            value={email}
            disabled={submitting}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? "email-error" : undefined}
            onChange={(event) => {
              setEmail(event.target.value);
              setErrors((current) => ({ ...current, email: undefined }));
            }}
          />
          {errors.email ? <p className="field-error" id="email-error">{errors.email}</p> : null}
        </div>
        <div className="form-field">
          <label className="field-label" htmlFor="password">비밀번호</label>
          <input
            className="input"
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={submitting}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "password-error" : undefined}
            onChange={(event) => {
              setPassword(event.target.value);
              setErrors((current) => ({ ...current, password: undefined }));
            }}
          />
          {errors.password ? <p className="field-error" id="password-error">{errors.password}</p> : null}
        </div>
        <button className="button button-primary" type="submit" disabled={submitting}>
          {submitting ? "로그인 중" : "로그인"}
        </button>
      </form>
    </section>
  );
}
