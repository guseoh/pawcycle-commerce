export const MEMBER_AVAILABLE_ACTIONS = ["REQUEST_CANCELLATION", "REQUEST_RETURN"] as const;
export type AvailableAction = typeof MEMBER_AVAILABLE_ACTIONS[number];

export interface PricingBreakdown { originalAmount: number; subtotalAmount: number; discountAmount: number; shippingFee: number; finalAmount: number; paymentAmount: number }
export interface OrderItem { skuId: number; skuCodeSnapshot?: string | null; productNameSnapshot: string; skuNameSnapshot: string; unitPrice: number; quantity: number; lineAmount: number }
export interface OrderDetail { orderId: number; orderNumber: string; source: string; status: string; originalAmount: number; discountAmount: number; shippingFee: number; paymentAmount: number; recipientName: string | null; recipientPhone: string | null; postalCode: string | null; addressLine1: string | null; addressLine2: string | null; createdAt: string; paidAt: string | null; items: OrderItem[]; payment: { paymentId: number; type: string; provider: string; status: string; amount: number; attemptNo: number; providerStatus?: string | null } | null; delivery: { deliveryId: number; status: string; carrierCode?: string; trackingNumber?: string; shippedAt?: string | null; deliveredAt?: string | null } | null; cancellation?: { cancellationId: number; status: string; reason?: string | null; requestedAt?: string | null; completedAt?: string | null } | null; return?: { returnId: number; status: string; reason?: string | null; rejectionReason?: string | null; restock?: boolean | null; requestedAt?: string | null; receivedAt?: string | null; completedAt?: string | null } | null; refunds?: Array<{ refundId: number; source?: string | null; status: string; attemptNo: number; amount: number }>; availableActions?: AvailableAction[] }
export interface OrderSummary { orderId: number; orderNumber: string; status: string; paymentAmount: number; createdAt: string }
export interface QuickReorderItem { skuId: number; quantity: number }
export interface QuickReorderSkippedItem extends QuickReorderItem { reason: string }
export interface QuickReorderResult { addedItems: QuickReorderItem[]; skippedItems: QuickReorderSkippedItem[]; cartVersion: number }
export interface CancellationResult { cancellationId: number; status: string; reason: string; requestedAt: string; completedAt: string | null }
export interface ReturnResult { returnId: number; status: string; reason: string; rejectionReason: string | null; restock: boolean | null; requestedAt: string; decidedAt: string | null; receivedAt: string | null; completedAt: string | null }

export interface Notification { notificationId: number; type: string; referenceType: string; referenceId: number; subscriptionId?: number | null; scheduledDate?: string | null; readAt: string | null; createdAt: string }
export interface Operation { type: string; referenceId: number; createdAt: string; attemptNo?: number | null; availableActions: string[] }
export interface CartItem { skuId: number; quantity: number; skuCode: string; skuName: string; price: number; unitPrice: number; lineAmount: number; productId: number; productName: string; availableQuantity: number; purchasable: boolean }
export interface CartResult { items: CartItem[]; pricing: PricingBreakdown; version: number }
export interface WishlistItem { productId: number; productName: string; createdAt: string }
export interface MemberCoupon { memberCouponId: number; couponId: number; name: string; discountType: "FIXED_AMOUNT" | "PERCENTAGE"; discountValue: number; status: "AVAILABLE" | "RESERVED" | "USED"; validFrom: string; validUntil: string }
export interface AddressRequest { name: string; recipientName: string; recipientPhone: string; postalCode: string; addressLine1: string; addressLine2: string }
export interface SubscriptionShippingAddressRequest { name?: string; recipientName: string; recipientPhone: string; postalCode: string; addressLine1: string; addressLine2: string }
export interface Address extends AddressRequest { addressId: number; isDefault: boolean }
export interface CheckoutResult { orderId: number; orderNumber: string; paymentId: number; providerOrderId: string; orderName: string; amount: number; pricing?: PricingBreakdown; tossTestEnabled: boolean }
export interface TossConfirmResult { paymentId: number; orderId: number; status: "SUCCEEDED" | "FAILED" | "UNKNOWN" }
export interface BillingMethodStatus { provider: "TOSS"; configured: boolean; registered: boolean }
