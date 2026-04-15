(function () {
  "use strict";

  /**
   * Cap consecutive blank lines so pasted walls of newlines don't dominate, but still allow one
   * visible blank line between paragraphs.
   *
   * Internal runs (between non-blank text): at most one blank line — e.g. a\\n\\n\\nb → a\\n\\nb.
   * Trailing runs (blank lines at end while typing): at most two split lines — "para\\n\\n" stays
   * as two newlines so the next paragraph can start after a real gap; a third Enter still collapses
   * to two. Without this, "para\\n\\n" became "para\\n" and the next line merged flush with no gap.
   */
  function collapseWithMap(before) {
    var oldLines = before.split("\n");
    var n = oldLines.length;
    var newLines = [];
    var oldLineToNewLine = new Array(n);
    function isBlank(idx) {
      return oldLines[idx].trim() === "";
    }
    var oi = 0;
    while (oi < n) {
      if (!isBlank(oi)) {
        oldLineToNewLine[oi] = newLines.length;
        newLines.push(oldLines[oi]);
        oi++;
        continue;
      }
      var start = oi;
      while (oi < n && isBlank(oi)) {
        oi++;
      }
      var end = oi - 1;
      var runLen = end - start + 1;
      var touchesStart = start === 0;
      var touchesEnd = end === n - 1;
      var maxOut;
      if (touchesEnd && !touchesStart) {
        maxOut = Math.min(runLen, 2);
      } else if (touchesStart && !touchesEnd) {
        maxOut = Math.min(runLen, 1);
      } else if (touchesStart && touchesEnd) {
        maxOut = Math.min(runLen, 2);
      } else {
        maxOut = Math.min(runLen, 1);
      }
      var k;
      for (k = 0; k < maxOut; k++) {
        oldLineToNewLine[start + k] = newLines.length;
        newLines.push("");
      }
      var lastKeptNew = newLines.length - 1;
      for (; k < runLen; k++) {
        oldLineToNewLine[start + k] = lastKeptNew;
      }
    }
    return {
      after: newLines.join("\n"),
      newLines: newLines,
      oldLines: oldLines,
      oldLineToNewLine: oldLineToNewLine,
    };
  }

  function posToLineCol(text, pos) {
    var lines = text.split("\n");
    var p = 0;
    for (var i = 0; i < lines.length; i++) {
      var end = p + lines[i].length;
      if (pos <= end) {
        return { line: i, col: pos - p };
      }
      p = end + 1;
    }
    var last = lines.length - 1;
    return {
      line: Math.max(0, last),
      col: lines[last] ? lines[last].length : 0,
    };
  }

  function lineColToPos(lines, line, col) {
    var p = 0;
    for (var i = 0; i < line && i < lines.length; i++) {
      p += lines[i].length + 1;
    }
    if (line >= 0 && line < lines.length) {
      p += Math.min(col, lines[line].length);
    }
    return p;
  }

  function mapPosition(before, result, pos) {
    var after = result.after;
    if (pos <= 0) {
      return 0;
    }
    if (pos >= before.length) {
      return after.length;
    }
    var oldLines = result.oldLines;
    var newLines = result.newLines;
    var mapLn = result.oldLineToNewLine;
    var lc = posToLineCol(before, pos);
    var newLine = mapLn[lc.line];
    var newCol = lc.col;
    if (
      lc.line > 0 &&
      mapLn[lc.line] === mapLn[lc.line - 1] &&
      oldLines[lc.line].trim() === ""
    ) {
      newCol = Math.min(newCol, newLines[newLine].length);
    }
    return Math.min(after.length, lineColToPos(newLines, newLine, newCol));
  }

  function applyToTextarea(textarea) {
    if (!textarea || textarea.disabled || textarea.readOnly) {
      return;
    }
    var before = textarea.value;
    var result = collapseWithMap(before);
    var after = result.after;
    if (before === after) {
      return;
    }
    var start = textarea.selectionStart;
    var end = textarea.selectionEnd;
    var scrollTop = textarea.scrollTop;
    textarea.value = after;
    var ns = mapPosition(before, result, start);
    var ne = mapPosition(before, result, end);
    try {
      textarea.setSelectionRange(ns, ne);
    } catch (err) {
      /* ignore */
    }
    textarea.scrollTop = scrollTop;
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
  }

  document.addEventListener(
    "input",
    function (e) {
      if (e.isComposing) {
        return;
      }
      var t = e.target;
      if (
        !t ||
        !t.classList ||
        !t.classList.contains("js-textarea-single-blank-max")
      ) {
        return;
      }
      applyToTextarea(t);
    },
    false,
  );
})();
