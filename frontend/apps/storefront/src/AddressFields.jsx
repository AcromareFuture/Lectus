import React from 'react';
import { provinces, regionOptions } from './address-regions';
import { useI18n } from './i18n';
import './address-fields.css';

export default function AddressFields({ value, onChange, disabled = false, maxLength = 180 }) {
  const { t } = useI18n();
  const { cities, districts } = regionOptions(value);
  const provinceSelected = provinces.some(item => item.name === value.province);
  const citySelected = cities.some(item => item.name === value.city);
  const districtSelected = districts.some(item => item.name === value.district);
  const legacyRegion = (value.province && !provinceSelected) || (value.city && !citySelected) || (value.district && !districtSelected);
  return <div className="address-fields">
    <div className="address-region-grid" role="group" aria-label={t('所在地区')}>
      <label>{t('省份')}<select name="province" autoComplete="address-level1" required value={provinceSelected ? value.province : ''} disabled={disabled} onChange={event => onChange({ ...value, province: event.target.value, city: '', district: '' })}>
        <option value="">{t('请选择省份')}</option>{provinces.map(item => <option key={item.code} value={item.name}>{item.name}</option>)}
      </select></label>
      <label>{t('城市')}<select name="city" autoComplete="address-level2" required value={citySelected ? value.city : ''} disabled={disabled || !provinceSelected} onChange={event => onChange({ ...value, city: event.target.value, district: '' })}>
        <option value="">{t(provinceSelected ? '请选择城市' : '请先选择省份')}</option>{cities.map(item => <option key={item.code} value={item.name}>{item.name}</option>)}
      </select></label>
      <label>{t('区 / 县')}<select name="district" autoComplete="address-level3" required value={districtSelected ? value.district : ''} disabled={disabled || !citySelected} onChange={event => onChange({ ...value, district: event.target.value })}>
        <option value="">{t(citySelected ? '请选择区县' : '请先选择城市')}</option>{districts.map(item => <option key={item.code} value={item.name}>{item.name}</option>)}
      </select></label>
    </div>
    {legacyRegion && <p className="address-region-note" role="status">{t('原地址地区')}：{[value.province, value.city, value.district].filter(Boolean).join(' ')}。{t('请重新选择所在地区，详细地址将保留。')}</p>}
    <label>{t('详细地址')}<textarea name="detail" required rows={3} maxLength={maxLength} autoComplete="street-address" value={value.detail || ''} disabled={disabled} onChange={event => onChange({ ...value, detail: event.target.value })} placeholder={t('街道、小区、楼栋及门牌号')} /></label>
  </div>;
}
