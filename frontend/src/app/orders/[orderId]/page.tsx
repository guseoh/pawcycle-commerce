import { CommerceOrderDetail } from "@/components/commerce-order-detail";
export default async function OrderPage({params}:{params:Promise<{orderId:string}>}){const {orderId}=await params;return <CommerceOrderDetail orderId={orderId}/>;}
