import { Mvp2SubscriptionDetail } from "@/components/mvp2-subscription-detail";

interface SubscriptionDetailPageProps {
  params: Promise<{ subscriptionId: string }>;
  searchParams: Promise<{ created?: string | string[]; replayed?: string | string[] }>;
}

export default async function SubscriptionDetailPage({ params, searchParams }: SubscriptionDetailPageProps) {
  const [{ subscriptionId }, query] = await Promise.all([params, searchParams]);
  return (
      <Mvp2SubscriptionDetail
      key={subscriptionId}
      subscriptionId={subscriptionId}
      created={query.created === "1"}
      replayed={query.replayed === "1"}
      />
  );
}
