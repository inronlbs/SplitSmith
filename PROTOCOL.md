# Project Operational Protocol: SplitSmith

To maintain complete control, clarity, and safety across all design, code, and build workflows, all agents working on this project MUST strictly follow this operational protocol:

---

### 1. No Immediate Fix Implementation
- **NEVER** edit source code, modify schemas, or execute implementation as soon as a query, issue, or feature request is received.

### 2. Analyze & Understand First
- First, perform a thorough, read-only analysis of the codebase, dependencies, and execution flows to identify the exact root cause or design requirements.

### 3. Explain & Propose Solution
- Present a clear, non-technical and technical explanation of the issue, root cause, and proposed resolution to the user.

### 4. Maintain `implementation_plan.md`
- Document the proposed technical changes, file modifications, and verification steps in `implementation_plan.md`.
- Include any clarifying questions or design choices directly in the plan for user review.

### 5. Iterate on User Queries & Feedback
- If the user provides additional input, new requirements, or feedback, re-analyze the codebase and update the `implementation_plan.md` accordingly before taking any action.

### 6. Wait for Explicit User Approval
- **DO NOT** modify any project code until the user explicitly approves the implementation plan and instructs you to proceed with implementation.

### 7. No Automatic Version Bumps or APK Builds
- After code implementation is complete and verified, **DO NOT** automatically bump version numbers, compile release APKs, or deploy updates to GitHub / Firebase.
- Always ask the user if they want to proceed with building/releasing or make further changes first.

### 8. Record Walkthrough & Clean Plan
- Once implementation is approved and complete, record all accomplished changes and verification results in `walkthrough.md`.
- Reset / clean `implementation_plan.md` so it is ready for the next feature or fix.

---

*This protocol is active and binding for all development on SplitSmith.*
