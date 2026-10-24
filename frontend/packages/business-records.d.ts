export function eventDetail(event: { key: string; detail?: string }): string;
export function auditAction(action: string): string;
export function transactionReference(value?: string): string | undefined;
export function serviceMessage(message?: string): string | undefined;
export function businessNote(value?: string): string;
