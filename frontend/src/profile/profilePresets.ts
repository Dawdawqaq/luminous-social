export interface ProfileImagePreset {
  id: string;
  name: string;
  url: string;
}

export interface ProfilePresetCatalog {
  avatars: ProfileImagePreset[];
  banners: ProfileImagePreset[];
}
