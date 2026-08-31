import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ResumeCertificationRequest } from "@/types/resume";

export function ResumeCertificationFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeCertificationRequest;
  onChange: (patch: Partial<ResumeCertificationRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5">
        <Label>Certification name *</Label>
        <Input
          value={value.name}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Issuer</Label>
        <Input
          value={value.issuer ?? ""}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ issuer: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Issue date</Label>
        <Input
          type="date"
          value={value.issueDate ?? ""}
          disabled={disabled}
          onChange={(e) => onChange({ issueDate: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Expiry date</Label>
        <Input
          type="date"
          value={value.expiryDate ?? ""}
          disabled={disabled}
          onChange={(e) => onChange({ expiryDate: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Credential ID</Label>
        <Input
          value={value.credentialId ?? ""}
          maxLength={120}
          disabled={disabled}
          onChange={(e) => onChange({ credentialId: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Credential URL</Label>
        <Input
          value={value.credentialUrl ?? ""}
          maxLength={255}
          disabled={disabled}
          onChange={(e) => onChange({ credentialUrl: e.target.value || null })}
          placeholder="https://…"
        />
      </div>
    </div>
  );
}
