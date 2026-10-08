/** Same rule as the backend's @StrongPassword. */
export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/
export const PASSWORD_HINT = '8 to 72 characters, with an uppercase letter, a lowercase letter, a digit and a symbol'
