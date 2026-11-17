import { cva, type VariantProps } from "class-variance-authority";
import type { ButtonHTMLAttributes } from "react";
import { cn } from "../cn";

/** Uses design tokens from styles/tokens.css so every theme restyles it automatically. */
export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-full font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-(--sb-color-primary) disabled:pointer-events-none disabled:opacity-50 motion-reduce:transition-none",
  {
    variants: {
      variant: {
        primary:
          "bg-(--sb-color-primary) text-(--sb-color-on-primary) shadow-sm hover:-translate-y-0.5 hover:shadow-md motion-reduce:hover:translate-y-0",
        outline:
          "border border-(--sb-color-primary) text-(--sb-color-primary) hover:bg-(--sb-color-primary) hover:text-(--sb-color-on-primary)",
        ghost: "text-(--sb-color-primary) hover:bg-black/5",
      },
      size: { sm: "h-9 px-4 text-sm", md: "h-11 px-6 text-base", lg: "h-13 px-8 text-lg" },
    },
    defaultVariants: { variant: "primary", size: "md" },
  },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants>;

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
