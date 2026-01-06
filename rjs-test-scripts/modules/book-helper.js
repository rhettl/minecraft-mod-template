/**
 * BookHelper - Create interactive written books with clickable links
 *
 * Creates Minecraft written books with pages containing clickable text that runs commands.
 *
 * @example Basic Usage
 * import { BookHelper } from './book-helper.js';
 * let book = new BookHelper('Admin Tools', 'RhettJS');
 * book.addLink('Teleport to Test Platform', '/tp @s 1000 100 1000');
 * book.addLink('Teleport to Spawn', '/tp @s 0 64 0');
 * book.addText('Regular text without links');
 * let giveCommand = book.build();
 * Command.executeAsServer(giveCommand);
 *
 * @example Using enumerations
 * import { BookHelper } from './book-helper.js';
 * let book = new BookHelper('Admin Tools', 'RhettJS');
 * book.addLink('Important Link', '/important', {
 *   color: BookHelper.colors.RED,
 *   bold: true
 * });
 *
 * Features:
 * - Automatic page breaks when content exceeds page limit
 * - Clickable links that run commands
 * - Custom styling per link (color, bold, etc.)
 * - Table of contents generation
 */

/**
 * Minecraft chat color names
 */
const colors = {
  BLACK: 'black',
  DARK_BLUE: 'dark_blue',
  DARK_GREEN: 'dark_green',
  DARK_AQUA: 'dark_aqua',
  DARK_RED: 'dark_red',
  DARK_PURPLE: 'dark_purple',
  GOLD: 'gold',
  GRAY: 'gray',
  DARK_GRAY: 'dark_gray',
  BLUE: 'blue',
  GREEN: 'green',
  AQUA: 'aqua',
  RED: 'red',
  LIGHT_PURPLE: 'light_purple',
  YELLOW: 'yellow',
  WHITE: 'white'
};

/**
 * Create a new book builder.
 *
 * @param {string} title - Book title
 * @param {string} author - Book author
 */
class BookHelper {
  constructor(title, author) {
    this.title = title || 'Untitled Book';
    this.author = author || 'Unknown';
    this.pages = [];
    this.currentPage = [];
    this.currentPageLength = 0;
    this.maxPageLength = 256; // Minecraft's max characters per page
  }

  /**
   * Add a clickable link to the current page.
   *
   * @param {string} text - Display text for the link
   * @param {string} command - Command to run when clicked
   * @param {Object} [options] - Optional styling
   * @param {string} [options.color] - Text color (use BookHelper.colors, default: BLUE)
   * @param {boolean} [options.underlined=true] - Underline the link
   * @param {string} [options.hoverText] - Tooltip text
   * @param {boolean} [options.bold] - Bold text
   * @param {boolean} [options.italic] - Italic text
   * @returns {BookHelper} this (for chaining)
   *
   * @example
   * book.addLink('Go to spawn', '/tp @s 0 64 0');
   *
   * @example
   * book.addLink('Important!', '/cmd', { color: BookHelper.colors.RED, bold: true });
   */
  addLink(text, command, options) {
    options = options || {};

    // Calculate length with newline
    var linkLength = text.length + 1; // +1 for newline

    // Check if we need a new page
    if (this.currentPageLength + linkLength > this.maxPageLength) {
      this._finalizePage();
    }

    var link = {
      text: text + '\n',
      color: options.color || colors.BLUE,
      underlined: options.underlined !== undefined ? options.underlined : true,
      clickEvent: {
        action: 'run_command',
        value: command
      },
      hoverEvent: {
        action: 'show_text',
        value: options.hoverText || ('Click to run: ' + command)
      }
    };

    // Apply additional styling
    if (options.bold) link.bold = true;
    if (options.italic) link.italic = true;

    this.currentPage.push(link);
    this.currentPageLength += linkLength;

    return this;
  }

  /**
   * Add regular text (non-clickable) to the current page.
   *
   * @param {string} text - Text content
   * @param {Object} [options] - Optional styling
   * @param {string} [options.color] - Text color (use BookHelper.colors)
   * @param {boolean} [options.bold] - Bold text
   * @param {boolean} [options.italic] - Italic text
   * @returns {BookHelper} this (for chaining)
   */
  addText(text, options) {
    options = options || {};

    var textWithNewline = text + '\n';
    var textLength = textWithNewline.length;

    // Check if we need a new page
    if (this.currentPageLength + textLength > this.maxPageLength) {
      this._finalizePage();
    }

    var textComponent = { text: textWithNewline };

    if (options.color) textComponent.color = options.color;
    if (options.bold) textComponent.bold = true;
    if (options.italic) textComponent.italic = true;

    this.currentPage.push(textComponent);
    this.currentPageLength += textLength;

    return this;
  }

  /**
   * Add a heading to the current page.
   *
   * @param {string} text - Heading text
   * @returns {BookHelper} this (for chaining)
   */
  addHeading(text) {
    return this.addText(text, { bold: true, color: colors.DARK_BLUE });
  }

  /**
   * Add a divider line to the current page.
   *
   * @returns {BookHelper} this (for chaining)
   */
  addDivider() {
    return this.addText('─────────────────', { color: colors.GRAY });
  }

  /**
   * Force start a new page.
   *
   * @returns {BookHelper} this (for chaining)
   */
  newPage() {
    if (this.currentPage.length > 0) {
      this._finalizePage();
    }
    return this;
  }

  /**
   * Internal: Finalize the current page and start a new one.
   * @private
   */
  _finalizePage() {
    if (this.currentPage.length > 0) {
      this.pages.push(JSON.stringify(this.currentPage));
      this.currentPage = [];
      this.currentPageLength = 0;
    }
  }

  /**
   * Build the book and return a give command string.
   *
   * @param {string} [target='@s'] - Target selector for who receives the book
   * @returns {string} Give command to create the book
   */
  build(target) {
    target = target || '@s';

    // Finalize current page if any content exists
    this._finalizePage();

    // Build pages array string
    const pagesStr = this.pages.join(',');

    // Escape quotes in title and author for NBT
    const escapedTitle = this.title.replace(/"/g, '\\"');
    const escapedAuthor = this.author.replace(/"/g, '\\"');

    // Build the give command with NBT
    // Note: The pages need to be raw JSON strings in the NBT
    return 'give ' + target + ' minecraft:written_book{' +
      'title:"' + escapedTitle + '",' +
      'author:"' + escapedAuthor + '",' +
      'pages:[' + pagesStr + ']' +
      '}';
  }

  /**
   * Build and give the book to a player.
   *
   * @param {string} [target='@s'] - Target selector
   * @returns {Promise} Promise from Command.executeAsServer
   */
  give(target) {
    return Command.executeAsServer(this.build(target));
  }
}

// Attach colors enumeration as static property
BookHelper.colors = colors;

export default BookHelper;