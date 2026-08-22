import { handleSummaryFor, operations, optionsForReadProfile, setup } from "./lib/phase8d.js";
export const options = optionsForReadProfile("sustained");
export { setup };
export const products = operations.products; export const productDetail = operations.productDetail; export const subscriptions = operations.subscriptions; export const cart = operations.cart; export const wishlist = operations.wishlist; export const orders = operations.orders; export const member = operations.member;
export function handleSummary(data) { return handleSummaryFor("sustained", data); }
