"use client";

import { useEffect, useState } from "react";
import { GraduationCap } from "lucide-react";
import { sb, StudioError } from "@/lib/studio";
import { Btn, inputCls, useAction } from "@/components/kit";

type Section = { id: string; label: string };

/** Turns an offered / admitted lead into a student record (school admins). */
export function EnrolAsStudent({ leadId, onDone }: { leadId: string; onDone: () => void }) {
  const [sections, setSections] = useState<Section[] | null>(null);
  const [sectionId, setSectionId] = useState("");
  const [done, setDone] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const { busy, run } = useAction();

  useEffect(() => {
    sb<Section[]>("sis/sections")
      .then((s) => {
        setSections(s);
        setSectionId(s[0]?.id ?? "");
      })
      .catch((e) => {
        if (e instanceof StudioError && (e.status === 403 || e.status === 404))
          setUnavailable(true);
      });
  }, []);

  if (unavailable || !sections) return null;
  if (done)
    return (
      <p className="mt-4 rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
        Enrolled as {done}. See Students.
      </p>
    );
  if (sections.length === 0)
    return (
      <p className="text-ink-muted mt-4 text-sm">
        Set up classes in Students to enrol this applicant.
      </p>
    );

  async function enrol() {
    const r = await run(
      "enrol",
      () =>
        sb<{ student: { admissionNo: string; name: string } }>(`sis/students/from-lead/${leadId}`, {
          body: { sectionId },
        }),
      "Enrolled as a student",
    );
    if (r) {
      setDone(`${r.student.name} (${r.student.admissionNo})`);
      onDone();
    }
  }

  return (
    <div className="mt-4 rounded-xl bg-emerald-50 p-3">
      <p className="mb-2 text-sm font-medium">Enrol as a student</p>
      <div className="flex flex-wrap gap-2">
        <select
          className={`${inputCls} w-auto`}
          value={sectionId}
          onChange={(e) => setSectionId(e.target.value)}
          aria-label="Class and section"
        >
          {sections.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label}
            </option>
          ))}
        </select>
        <Btn variant="primary" busy={busy === "enrol"} onClick={() => void enrol()}>
          <GraduationCap className="size-4" aria-hidden /> Enrol
        </Btn>
      </div>
      <p className="text-ink-muted mt-1 text-xs">
        Uses the application details (date of birth, address, parents) and marks the lead Admitted.
      </p>
    </div>
  );
}
