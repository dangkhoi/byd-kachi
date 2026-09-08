#!/usr/bin/env bash
# Shared interactive matrix runner for the on-car kit.
#
# Solves three things that otherwise cost vehicle time:
#   1. resume — a run interrupted at step 14 continues at step 14 instead of restarting at 1;
#   2. selection — a single failed case can be repeated with --only, without the other 22;
#   3. verdicts — each step records PASS/FAIL/SKIP plus a note, so the sign-off table is
#      generated instead of reconstructed from memory hours later.
#
# Caller contract:
#   MATRIX_STEPS=(C1 C2 ... F9)      # declare before matrix_init, used for progress + ordering
#   matrix_init "<name>" "$@"
#   matrix_step C1 "description" ["extra operator hint"] && capture "c1-name"
#   matrix_summary
#
# Resume: pass the previous evidence directory, e.g.
#   EVIDENCE_DIR=oncar-v2-20260726T101500Z scripts/vehicle/run-cast-matrix.sh

matrix_usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[1]:-$0}") [--only ID[,ID...]] [--from ID] [--list] [--redo]

  --only ID[,ID]  run just these steps (repeat one failed case cheaply)
  --from ID       start at this step and continue to the end
  --list          print the step list and exit, no device interaction
  --redo          re-run steps already recorded in this evidence directory

Resume an interrupted session by exporting the same EVIDENCE_DIR; completed steps are
skipped automatically unless --redo is given.

At each step: p = pass, f = fail, s = skip, q = stop and keep progress.
Anything typed after the letter is stored as the note, e.g. "f gauges did not return".
EOF
}

# Parsed before any device interaction so --list/--help work off-car.
matrix_parse_args() {
  MATRIX_ONLY="${MATRIX_ONLY:-}"
  MATRIX_FROM="${MATRIX_FROM:-}"
  MATRIX_LIST=0
  MATRIX_REDO=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --only) [[ -n "${2:-}" ]] || { echo "ERROR: --only needs step ids" >&2; exit 2; }
              MATRIX_ONLY="$2"; shift 2 ;;
      --from) [[ -n "${2:-}" ]] || { echo "ERROR: --from needs a step id" >&2; exit 2; }
              MATRIX_FROM="$2"; shift 2 ;;
      --list) MATRIX_LIST=1; shift ;;
      --redo) MATRIX_REDO=1; shift ;;
      -h|--help) matrix_usage; exit 0 ;;
      *) echo "ERROR: unknown option: $1" >&2; matrix_usage >&2; exit 2 ;;
    esac
  done
}

matrix_init() {
  MATRIX_NAME="$1"
  MATRIX_TOTAL="${#MATRIX_STEPS[@]}"
  MATRIX_INDEX=0
  MATRIX_REACHED=0
  [[ -n "$MATRIX_FROM" ]] || MATRIX_REACHED=1

  if matrix_listing; then
    echo "Steps for $MATRIX_NAME ($MATRIX_TOTAL):"
    return 0
  fi
  MATRIX_RESULTS="$EVIDENCE_DIR/results.tsv"
  if [[ ! -f "$MATRIX_RESULTS" ]]; then
    printf 'step\tverdict\tiso_utc\tnote\n' > "$MATRIX_RESULTS"
  else
    echo "Resuming in $EVIDENCE_DIR ($(( $(wc -l < "$MATRIX_RESULTS") - 1 )) step(s) already recorded)"
  fi
  echo "Matrix: $MATRIX_NAME · $MATRIX_TOTAL steps · results -> $MATRIX_RESULTS"
}

matrix_listing() {
  [[ "${MATRIX_LIST:-0}" -eq 1 ]]
}

matrix_recorded() {
  awk -F'\t' -v id="$1" 'NR>1 && $1==id {found=1} END{exit !found}' "$MATRIX_RESULTS"
}

# Returns 0 when the caller should perform the step's capture.
matrix_step() {
  local id="$1" description="$2" hint="${3:-}"
  MATRIX_INDEX=$(( MATRIX_INDEX + 1 ))

  if matrix_listing; then
    printf '  %-4s %s\n' "$id" "$description"
    return 1
  fi
  if [[ -n "$MATRIX_FROM" && "$MATRIX_REACHED" -eq 0 ]]; then
    [[ "$id" == "$MATRIX_FROM" ]] && MATRIX_REACHED=1 || return 1
  fi
  if [[ -n "$MATRIX_ONLY" ]] && [[ ",$MATRIX_ONLY," != *",$id,"* ]]; then
    return 1
  fi
  if [[ "$MATRIX_REDO" -eq 0 ]] && matrix_recorded "$id"; then
    echo "  [$MATRIX_INDEX/$MATRIX_TOTAL] $id — already recorded, skipping (use --redo to repeat)"
    return 1
  fi

  echo
  echo "=== [$MATRIX_INDEX/$MATRIX_TOTAL] $id — $description ==="
  [[ -z "$hint" ]] || echo "    $hint"
  local answer verdict note
  while :; do
    if ! read -r -p "    verdict [p=pass f=fail s=skip q=stop] + optional note: " answer; then
      answer="q"
      echo
    fi
    case "${answer:0:1}" in
      p|P) verdict="PASS" ;;
      f|F) verdict="FAIL" ;;
      s|S) verdict="SKIP" ;;
      q|Q) echo "    stopping; $(( MATRIX_TOTAL - MATRIX_INDEX + 1 )) step(s) left. Resume with EVIDENCE_DIR=$EVIDENCE_DIR"
           matrix_summary
           exit 0 ;;
      *) echo "    answer with p, f, s or q"; continue ;;
    esac
    note="$(printf '%s' "${answer:1}" | sed 's/^[[:space:]]*//')"
    break
  done
  printf '%s\t%s\t%s\t%s\n' "$id" "$verdict" "$(date -u +%FT%TZ)" "$note" >> "$MATRIX_RESULTS"
  echo "    recorded $id=$verdict"
  return 0
}

matrix_summary() {
  matrix_listing && return 0
  local pass fail skip
  pass="$(awk -F'\t' 'NR>1 && $2=="PASS"{n++} END{print n+0}' "$MATRIX_RESULTS")"
  fail="$(awk -F'\t' 'NR>1 && $2=="FAIL"{n++} END{print n+0}' "$MATRIX_RESULTS")"
  skip="$(awk -F'\t' 'NR>1 && $2=="SKIP"{n++} END{print n+0}' "$MATRIX_RESULTS")"
  echo
  echo "MATRIX_RESULT $MATRIX_NAME pass=$pass fail=$fail skip=$skip of $MATRIX_TOTAL"
  echo "Paste-ready sign-off rows:"
  awk -F'\t' 'NR>1 {printf "| %s | %s | %s | %s |\n", $1, $2, ENVIRON["EVIDENCE_DIR"], $4}' "$MATRIX_RESULTS"
  if [[ "$fail" -gt 0 ]]; then
    echo
    echo "Repeat only the failures with:"
    echo "  EVIDENCE_DIR=$EVIDENCE_DIR $0 --redo --only $(awk -F'\t' 'NR>1 && $2=="FAIL"{printf "%s%s", sep, $1; sep=","}' "$MATRIX_RESULTS")"
  fi
}
