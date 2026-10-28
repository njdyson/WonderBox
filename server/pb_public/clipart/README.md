Clipart catalogue for quiz pictures: [Fluent Emoji](https://github.com/microsoft/fluentui-emoji)
by Microsoft, MIT licensed (LICENSE.txt). `fluent.json` lists names, keywords and picture paths
and is made by `tools/make_clipart_index.py`. The pictures themselves are fetched by the phone
app when a parent picks one, and copied to the server as quiz images.

Country flags come from [flag-icons](https://github.com/lipis/flag-icons) (MIT, flags/LICENSE.txt).
`tools/make_flags.js` draws them to PNGs in `flags/` and lists them in `flags.json`; unlike
Fluent Emoji they are served from the server itself.
