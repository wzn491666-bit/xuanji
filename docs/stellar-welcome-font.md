# Welcome Typography

Each new session draws one literary welcome sentence associated with one of the
16 stellar domains. Its wording, font and composition stay fixed within the
session, including when an empty session is restored. No extra welcome subtitle
is displayed.

Eight real Chinese typefaces are bundled as renamed glyph subsets, all under
SIL Open Font License 1.1. Complete licenses and original copyrights live in
`app/src/main/assets/licenses/`. Chat replies and controls keep readable UI type.

| Font | Character | Domains |
| --- | --- | --- |
| LXGW WenKai | Calm handwritten regular | 启明、天梁、瑶光 |
| ZCOOL XiaoWei | Incised, expressive serif | 天枢、开阳 |
| Ma Shan Zheng | Confident brushwork | 破军 |
| Noto Serif SC | Precise classical serif | 天权、华盖、天府 |
| ZCOOL QingKe HuangYou | Contemporary narrow lettering | 天机、辅 |
| Long Cang | Open flowing strokes | 长庚 |
| Zhi Mang Xing | Energetic running script | 天璇、文曲 |
| Liu Jian Mao Cao | Spacious cursive | 七杀、太一 |

Official source: https://github.com/lxgw/LxgwWenKai

Upstream asset:
https://github.com/lxgw/LxgwWenKai/releases/download/v1.522/LXGWWenKai-Regular.ttf

Upstream SHA256:
`39ad71264b588165b469e35e6afb162a378dacd1f95348160240ba9038ac3009`

Regenerate with Python and fonttools:

```text
python tools/subset_welcome_font.py app/build/visual-checks/fonts/LXGWWenKai-Regular.ttf app/src/main/res/font/stellar_wenkai.ttf
```

The other seven fonts come from their corresponding `ofl/` directories in the
official https://github.com/google/fonts repository. Input filenames and exact
SHA256 digests are pinned in the script. Change its input/output arguments for
each family. Noto Serif SC is frozen to its regular weight after subsetting.

The script validates each official input digest, includes printable characters
from StellarWelcomeScene.kt, and checks that every required glyph remains.
Regenerate all eight resources whenever a welcome verse changes. Subsets use
distinct Stellar family names; original copyright and license records remain.
Iansui was inspected and excluded because it lacks required simplified glyphs.

Welcome content refers to the 16 domains read from the official homepage's
clickable portraits and https://tianshuharness.com/docs/concepts on 2026-10-02.
The literary scene is independent of runtime domain selection. New sessions
still follow the harness's own configured domain; no decorative choice sends
`/domain` or changes the model.
