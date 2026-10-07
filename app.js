/**
 * VOCIS Interactive Showcase & Workflow Controller
 */

document.addEventListener('DOMContentLoaded', () => {
  // Screen views map
  const screens = {
    splash: document.getElementById('view-splash'),
    onboarding: document.getElementById('view-onboarding'),
    permissions: document.getElementById('view-permissions'),
    dashboard: document.getElementById('view-dashboard'),
    calls: document.getElementById('view-calls'),
    voices: document.getElementById('view-voices'),
    enrollment: document.getElementById('view-enrollment'),
    tools: document.getElementById('view-tools'),
    digitalArrest: document.getElementById('view-digital-arrest'),
    evidenceVault: document.getElementById('view-evidence'),
    emergency: document.getElementById('view-emergency'),
    settings: document.getElementById('view-settings')
  };

  const stepCards = document.querySelectorAll('.step-card');
  const bottomNavTabs = document.querySelectorAll('.nav-tab-item');

  function switchScreen(targetKey) {
    // Hide all screens
    Object.values(screens).forEach(screen => {
      if (screen) screen.classList.remove('active');
    });

    // Show target screen
    if (screens[targetKey]) {
      screens[targetKey].classList.add('active');
    }

    // Highlight corresponding workflow step card
    stepCards.forEach(card => {
      if (card.dataset.screen === targetKey) {
        card.classList.add('active');
      } else {
        card.classList.remove('active');
      }
    });

    // Highlight corresponding bottom tab if applicable
    bottomNavTabs.forEach(tab => {
      if (tab.dataset.tab === targetKey) {
        tab.classList.add('active');
      } else {
        tab.classList.remove('active');
      }
    });
  }

  // Step card clicks
  stepCards.forEach(card => {
    card.addEventListener('click', () => {
      const screenKey = card.dataset.screen;
      switchScreen(screenKey);
    });
  });

  // Bottom Navigation Tab clicks inside phone
  bottomNavTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetScreen = tab.dataset.tab;
      switchScreen(targetScreen);
    });
  });

  // Simulator Interactive Buttons
  const btnStartOnboarding = document.getElementById('sim-btn-get-started');
  if (btnStartOnboarding) {
    btnStartOnboarding.addEventListener('click', () => switchScreen('permissions'));
  }

  const btnEnablePermissions = document.getElementById('sim-btn-enable-protection');
  if (btnEnablePermissions) {
    btnEnablePermissions.addEventListener('click', () => switchScreen('dashboard'));
  }

  const btnOpenArrest = document.getElementById('sim-btn-open-arrest');
  if (btnOpenArrest) {
    btnOpenArrest.addEventListener('click', () => switchScreen('digitalArrest'));
  }

  const btnOpenSos = document.getElementById('sim-btn-open-sos');
  if (btnOpenSos) {
    btnOpenSos.addEventListener('click', () => switchScreen('emergency'));
  }

  const btnOpenEvidence = document.getElementById('sim-btn-open-evidence');
  if (btnOpenEvidence) {
    btnOpenEvidence.addEventListener('click', () => switchScreen('evidenceVault'));
  }

  const btnEnrollVoice = document.getElementById('sim-btn-enroll-voice');
  if (btnEnrollVoice) {
    btnEnrollVoice.addEventListener('click', () => switchScreen('enrollment'));
  }

  const btnFinishEnrollment = document.getElementById('sim-btn-finish-enroll');
  if (btnFinishEnrollment) {
    btnFinishEnrollment.addEventListener('click', () => {
      alert("Voice Embedding sealed in AES-256 Vault! Profile added.");
      switchScreen('voices');
    });
  }

  // Digital Arrest Step Advancement Simulator
  let currentArrestPhase = 1;
  const arrestPhaseTitle = document.getElementById('sim-arrest-phase-title');
  const arrestProgressBar = document.getElementById('sim-arrest-progress');
  const btnNextArrestPhase = document.getElementById('sim-btn-next-phase');

  const arrestPhases = [
    { num: 1, title: "Phase 1: Alert Detected (Extortion Coercion Identified)" },
    { num: 2, title: "Phase 2: Call Isolation Assessment" },
    { num: 3, title: "Phase 3: Authority Claim Verification (CBI/Police)" },
    { num: 4, title: "Phase 4: Legal Education (Police NEVER arrest on video)" },
    { num: 5, title: "Phase 5: Financial Coercion Check (RBI verification fake)" },
    { num: 6, title: "Phase 6: Evidence Collection" },
    { num: 7, title: "Phase 7: Deterministic Rule Evaluation (5 Violations)" },
    { num: 8, title: "Phase 8: Family SOS Confirmation" },
    { num: 9, title: "Phase 9: Forensic Report Generation (SHA-256 Sealed)" },
    { num: 10, title: "Phase 10: Defense Completed (Reported to 1930)" }
  ];

  if (btnNextArrestPhase) {
    btnNextArrestPhase.addEventListener('click', () => {
      currentArrestPhase++;
      if (currentArrestPhase > 10) {
        currentArrestPhase = 1;
        switchScreen('evidenceVault');
        return;
      }
      if (arrestPhaseTitle) {
        arrestPhaseTitle.textContent = arrestPhases[currentArrestPhase - 1].title;
      }
      if (arrestProgressBar) {
        arrestProgressBar.style.width = `${(currentArrestPhase / 10) * 100}%`;
      }
      if (currentArrestPhase === 9) {
        btnNextArrestPhase.textContent = "Seal SHA-256 & Finish";
      } else if (currentArrestPhase === 10) {
        btnNextArrestPhase.textContent = "View Forensic Vault →";
      } else {
        btnNextArrestPhase.textContent = "Next Recovery Step →";
      }
    });
  }

  // Live Threat Overlay Toggle Simulator
  const btnToggleOverlay = document.getElementById('sim-toggle-overlay');
  const floatingOverlayPill = document.getElementById('sim-floating-pill');
  if (btnToggleOverlay && floatingOverlayPill) {
    btnToggleOverlay.addEventListener('click', () => {
      if (floatingOverlayPill.style.display === 'none' || !floatingOverlayPill.style.display) {
        floatingOverlayPill.style.display = 'flex';
        btnToggleOverlay.textContent = "Hide Floating System Window";
      } else {
        floatingOverlayPill.style.display = 'none';
        btnToggleOverlay.textContent = "Show Floating System Window";
      }
    });
  }
});
