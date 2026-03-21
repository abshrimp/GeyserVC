# GeyserVC

GeyserMC上で動作する近接VCです

## 動作要件

- Java 21
- Paper 1.21.x
- Node.js（`node` コマンドが実行可能であること）
- https の独自ドメインが使えること（暗号化されていないとマイクが使用できません）

## 設定ファイル（config.yml）

```yml
ports:
  udp: 50000        # マイクラサーバーとNodeサーバーをこのポートで通信させます
  websocket: 8080   # Nodeサーバーとクライアントをこのポートで通信させます
  http: 3000        # 外部からをこのポートに飛ばせるようにしておく

livekit:
  api_key: "your_livekit_api_key"   # LiveKit Cloud の API を取得しておく
  api_secret: "your_livekit_api_secret"
  host: "your_livekit_host_url"
  room_name: "your_room_name"       # [option] LiveKit Cloud のルーム名

api:
  xbl_key: ""
  base_url: "https://your-domain.com"  # 公開ドメイン（証明書を発行して、http で指定したポートに飛ぶようにしておく）

voice:
  max_distance: 40  # 声が届く最大距離（m）

url:
  base: "https://your-domain.com"  # base_url と同じにしておく
```

## コマンド

- `/vcurl`
  - プレイヤー実行時: 自分用 VC URL を表示
  - サーバー実行時: `/vcurl <player_name>` で対象プレイヤーの URL を表示
