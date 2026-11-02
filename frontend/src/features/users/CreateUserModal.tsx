import { useEffect, useRef, useState } from "react";
import type React from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserCheck } from "lucide-react";

import Button from "../../components/ui/buttons/Button";
import Input from "../../components/ui/forms/Input";
import Select from "../../components/ui/forms/Select";
import Modal from "../../components/ui/overlay/Modal";
import PaymentConfirmationDialog from "../confirmation/PaymentConfirmationDialog";
import { isApiError, NETWORK_ERROR_STATUS } from "../../error/api.error";
import { zodIssuesToFieldErrors } from "../../lib/zodErrors";
import { createUser } from "./api";
import { CREATABLE_ROLE_OPTIONS, ROLE_LABELS } from "./roles";
import { createUserFormSchema, type CreateUserInput } from "./schema";
import { usersQueryKey } from "./usersQueryOptions";
import type { CreateUserFormData, CreateUserFormErrors, UserResponse } from "./types";

import styles from "./UsersPage.module.css";

const EMPTY_FORM: CreateUserFormData = { name: "", email: "", role: "", password: "" };

// Samma ordning som fälten visas i formuläret
const FIELD_ORDER = ["name", "email", "role", "password"] as const;

interface CreateUserModalProps {
  onClose: () => void;
}

export default function CreateUserModal({ onClose }: CreateUserModalProps) {
  const queryClient = useQueryClient();
  const [formData, setFormData] = useState<CreateUserFormData>(EMPTY_FORM);
  const [errors, setErrors] = useState<CreateUserFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [createdUser, setCreatedUser] = useState<UserResponse | null>(null);

  const nameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const roleRef = useRef<HTMLButtonElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const focusFirstErrorRef = useRef(false);

  // Körs efter att felen renderats, så att skärmläsare läser upp felet med fältet
  useEffect(() => {
    if (!focusFirstErrorRef.current) {
      return;
    }

    focusFirstErrorRef.current = false;

    const fieldRefs = {
      name: nameRef,
      email: emailRef,
      role: roleRef,
      password: passwordRef,
    };
    const firstInvalidField = FIELD_ORDER.find((field) => errors[field]);

    if (firstInvalidField) {
      fieldRefs[firstInvalidField].current?.focus();
    }
  }, [errors]);

  const mutation = useMutation({
    mutationFn: (input: CreateUserInput) => createUser(input),
    onSuccess: (user) => {
      void queryClient.invalidateQueries({ queryKey: usersQueryKey });
      setCreatedUser(user);
      setFormData(EMPTY_FORM);
      setErrors({});
    },
    onError: (error) => {
      console.error("Failed to create user:", error);

      // Backendens svar på dubblett: visa det vid e-postfältet
      if (isApiError(error) && error.status === 409) {
        setErrors({ email: error.message });
        focusFirstErrorRef.current = true;
        return;
      }

      setSubmitError(getSubmitErrorMessage(error));
    },
  });

  const updateField = (field: keyof CreateUserFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const handleSubmit = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitError(null);

    const result = createUserFormSchema.safeParse(formData);

    if (!result.success) {
      setErrors(zodIssuesToFieldErrors<keyof CreateUserFormErrors>(result.error));
      focusFirstErrorRef.current = true;
      return;
    }

    setErrors({});
    mutation.mutate(result.data);
  };

  if (createdUser) {
    // Samma bekräftelsevy som efter en skickad betalning
    return (
      <PaymentConfirmationDialog
        title="Användaren har skapats"
        description=""
        details={[]}
        confirmLabel=""
        onConfirm={onClose}
        onCancel={onClose}
        result={{
          title: "Användaren har skapats",
          description: "Användaren kan nu logga in med startlösenordet.",
          decoration: <UserCheck />,
          statusLabel: "Användaren har skapats",
          details: [
            { label: "Namn", value: createdUser.name },
            { label: "E-post", value: createdUser.email },
            { label: "Roll", value: ROLE_LABELS[createdUser.role] },
          ],
          actions: (
            <>
              <Button type="button" onClick={() => setCreatedUser(null)}>Skapa en till</Button>
              <Button type="button" variant="primary" onClick={onClose}>Klar</Button>
            </>
          ),
        }}
      />
    );
  }

  return (
    <Modal
      title="Skapa användare"
      description="Företagstillhörigheten sätts automatiskt."
      onClose={onClose}
      dismissible={!mutation.isPending}
    >
      <form onSubmit={handleSubmit} noValidate>
        <p className={styles.requiredLegend}>
          Fält markerade med <span className={styles.requiredMark} aria-hidden="true">*</span> är obligatoriska.
        </p>

        <Input
          ref={nameRef}
          label="Namn"
          required
          placeholder="För- och efternamn"
          autoComplete="off"
          value={formData.name}
          onChange={(event) => updateField("name", event.target.value)}
          error={errors.name}
        />

        <Input
          ref={emailRef}
          label="E-post"
          type="email"
          required
          placeholder="namn@foretag.se"
          autoComplete="off"
          value={formData.email}
          onChange={(event) => updateField("email", event.target.value)}
          error={errors.email}
        />

        <Select
          ref={roleRef}
          label="Roll"
          required
          placeholder="Välj roll"
          value={formData.role}
          options={CREATABLE_ROLE_OPTIONS}
          onChange={(value) => updateField("role", value)}
        />
        {errors.role && <p className={styles.fieldError}>{errors.role}</p>}

        <Input
          ref={passwordRef}
          label="Startlösenord"
          type="password"
          required
          placeholder="Minst 8 tecken"
          autoComplete="new-password"
          value={formData.password}
          onChange={(event) => updateField("password", event.target.value)}
          error={errors.password}
        />

        <div className={styles.actions}>
          {submitError && (
            <p className={styles.submitError} role="alert">
              {submitError}
            </p>
          )}
          <Button type="button" onClick={onClose} disabled={mutation.isPending}>
            Avbryt
          </Button>
          <Button
            type="submit"
            variant="primary"
            disabled={mutation.isPending}
            aria-busy={mutation.isPending}
          >
            {mutation.isPending ? "Skapar…" : "Skapa användare"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

const SERVER_UNREACHABLE_MESSAGE =
  "Kunde inte nå servern. Användaren har inte skapats. Försök igen om en stund.";

function getSubmitErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.status === NETWORK_ERROR_STATUS || error.status === 502 || error.status === 503) {
      return SERVER_UNREACHABLE_MESSAGE;
    }

    // Backendens eget meddelande, eller api.ts standardmeddelande för statuskoden
    return error.message;
  }

  return "Användaren kunde inte skapas. Försök igen.";
}
