import React from "react";

interface PasswordStrengthProps {
  password?: string;
}

export const PasswordStrength: React.FC<PasswordStrengthProps> = ({
  password = "",
}) => {
  const getStrength = (pwd: string) => {
    let score = 0;
    if (pwd.length >= 6) score += 1;
    if (pwd.length >= 10) score += 1;
    if (/[A-Z]/.test(pwd) && /[0-9]/.test(pwd)) score += 1;
    if (/[^A-Za-z0-9]/.test(pwd)) score += 1;
    return score; // 0, 1, 2, 3, 4
  };

  const score = getStrength(password);
  if (!password) return null;

  const labels = ["极弱", "弱", "适中", "安全", "非常坚固"];
  const colors = [
    "bg-terracotta",
    "bg-terracotta",
    "bg-ochre",
    "bg-[#5B8266]",
    "bg-[#5B8266]",
  ];

  return (
    <div className="space-y-1 pt-1">
      <div className="flex gap-1.5 h-1">
        {[0, 1, 2, 3].map((index) => (
          <div
            key={index}
            className={`h-full flex-1 rounded-full transition-all duration-300 ${
              index < score
                ? colors[score]
                : "bg-paper-300 dark:bg-night-border"
            }`}
          />
        ))}
      </div>
      <div className="flex justify-between items-center text-[10px] text-ink-subtle dark:text-night-subtle">
        <span>密码强度</span>
        <span className="font-medium">{labels[score]}</span>
      </div>
    </div>
  );
};
